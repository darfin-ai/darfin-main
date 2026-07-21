package com.kosta.darfin.service.community;

import com.kosta.darfin.entity.common.Stock;
import com.kosta.darfin.repository.common.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DartApiService {

    private static final String SK_HYNIX_STOCK_CODE = "000660";
    private static final String SK_HYNIX_DISPLAY_NAME = "SK하이닉스";

    @Value("${dart.api.key}")
    private String apiKey;

    @Value("${dart.api.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final StockRepository stockRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * DART OpenAPI에서 전체 기업 목록을 다운로드하여 stock 테이블에 동기화합니다.
     * corpCode.xml → ZIP 파일 → CORPCODE.xml 파싱 → 배치 upsert
     */
    @Transactional
    public int syncCorpList() {
        String url = baseUrl + "/corpCode.xml?crtfc_key=" + apiKey;
        log.info("DART 기업 목록 동기화 시작: {}", url);

        byte[] zipBytes = restTemplate.getForObject(url, byte[].class);
        if (zipBytes == null) {
            throw new RuntimeException("DART API 응답이 비어있습니다.");
        }

        List<Stock> stocks = parseCorpCodeZip(zipBytes);
        log.info("파싱 완료: {}개 기업", stocks.size());

        batchUpsert(stocks);
        log.info("동기화 완료: {}개 기업", stocks.size());
        return stocks.size();
    }

    /**
     * 키워드로 stock 테이블에서 기업을 검색합니다 (DB 검색, DART API 직접 호출 아님).
     * stock_code가 있는 실제 상장 종목만 반환합니다.
     */
    public List<Stock> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return stockRepository.findByStockCodeIsNotNullAndCompanyNameContainingIgnoreCaseOrderByCompanyNameAsc(keyword);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<Stock> parseCorpCodeZip(byte[] zipBytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("CORPCODE.xml".equals(entry.getName())) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    return parseXml(baos.toByteArray());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("DART corpCode.xml 파싱 중 오류 발생", e);
        }
        throw new RuntimeException("ZIP 파일에서 CORPCODE.xml을 찾을 수 없습니다.");
    }

    private List<Stock> parseXml(byte[] xmlBytes) {
        List<Stock> stocks = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xmlBytes));
            NodeList nodeList = doc.getElementsByTagName("list");

            for (int i = 0; i < nodeList.getLength(); i++) {
                Element el = (Element) nodeList.item(i);
                String corpCode = getText(el, "corp_code");
                String corpName = getText(el, "corp_name");
                String stockCode = getText(el, "stock_code");

                if (corpCode == null || corpCode.isBlank()) continue;

                stocks.add(Stock.builder()
                        .dartCorpCode(corpCode)
                        .companyName(canonicalCompanyName(stockCode, corpName))
                        .stockCode(stockCode == null || stockCode.isBlank() ? null : stockCode)
                        .marketType(null)
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("XML 파싱 오류", e);
        }
        return stocks;
    }

    private String getText(Element el, String tag) {
        NodeList nl = el.getElementsByTagName(tag);
        if (nl.getLength() == 0) return null;
        return nl.item(0).getTextContent().trim();
    }

    static String canonicalCompanyName(String stockCode, String corpName) {
        if (SK_HYNIX_STOCK_CODE.equals(stockCode)) {
            return SK_HYNIX_DISPLAY_NAME;
        }
        return corpName != null ? corpName : "";
    }

    /**
     * 이미 저장된 외부 원본 표기명을 서비스 표시명으로 교정합니다.
     * 애플리케이션 시작 시 실행되며 같은 값으로의 갱신은 발생하지 않습니다.
     */
    public int normalizeKnownCompanyNames() {
        return jdbcTemplate.update(
                "UPDATE stock SET company_name = ? WHERE stock_code = ? AND company_name <> ?",
                SK_HYNIX_DISPLAY_NAME,
                SK_HYNIX_STOCK_CODE,
                SK_HYNIX_DISPLAY_NAME
        );
    }

    // MariaDB ON DUPLICATE KEY UPDATE로 upsert 처리 (dartCorpCode unique 컬럼 기준)
    private void batchUpsert(List<Stock> stocks) {
        String sql = "INSERT INTO stock (dart_corp_code, company_name, stock_code, market_type) " +
                     "VALUES (?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE " +
                     "company_name = VALUES(company_name), " +
                     "stock_code = VALUES(stock_code)";

        int batchSize = 1000;
        for (int i = 0; i < stocks.size(); i += batchSize) {
            List<Stock> batch = stocks.subList(i, Math.min(i + batchSize, stocks.size()));
            jdbcTemplate.batchUpdate(sql, batch, batch.size(), (ps, stock) -> {
                ps.setString(1, stock.getDartCorpCode());
                ps.setString(2, stock.getCompanyName());
                ps.setString(3, stock.getStockCode());
                ps.setString(4, stock.getMarketType());
            });
        }
    }
}
