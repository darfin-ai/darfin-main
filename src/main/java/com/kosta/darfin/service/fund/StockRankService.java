package com.kosta.darfin.service.fund;

import com.kosta.darfin.dto.fund.StockSummaryDTO;
import com.kosta.darfin.entity.fund.StockInfo;
import com.kosta.darfin.repository.fund.StockInfoRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 4개 탭(거래대금/거래량/급상승/급하락) 순위를 만들어주는 서비스.
 * volume-rank API는 "거래량 기준" 데이터를 주므로, 거래대금/급상승/급하락은
 * 같은 원본 데이터를 받아서 우리 쪽에서 재정렬한다 (API 호출은 탭당 1회로 유지).
 *
 * StockSummaryDTO.pct 는 BigDecimal 타입.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockRankService {

    private static final int TOP_N = 14;

    private final KisRankApiClient kisRankApiClient;
    private final StockInfoRepository stockInfoRepository;
    private final StockInfoService stockInfoService;

    // 종목코드 → 업종명 캐시. 업종은 사실상 불변이라 서버 기동 중 무효화 불필요.
    // 값 "" = KIS에도 업종 정보가 없는 종목 (재시도 방지용 마커)
    private final ConcurrentHashMap<String, String> sectorCache = new ConcurrentHashMap<>();

    /** 거래대금 순위 — FID_BLNG_CLS_CODE=3(거래금액순)으로 직접 요청, value 내림차순 */
    public List<StockSummaryDTO> getTradeValueRank() {
        return toSortedDtoList(kisRankApiClient.fetchVolumeRank("3"),
                Comparator.comparing(StockSummaryDTO::getValue).reversed());
    }

    /** 거래량 순위 — KIS 응답 순서(거래량순) 그대로 사용 */
    public List<StockSummaryDTO> getVolumeRank() {
        return toDtoList(kisRankApiClient.fetchVolumeRank("0"));
    }

    /** 급상승 — 등락률 내림차순 */
    public List<StockSummaryDTO> getTopGainers() {
        return toSortedDtoList(kisRankApiClient.fetchVolumeRank("0"),
                Comparator.comparing(StockSummaryDTO::getPct).reversed());
    }

    /** 급하락 — 등락률 오름차순 */
    public List<StockSummaryDTO> getTopLosers() {
        return toSortedDtoList(kisRankApiClient.fetchVolumeRank("0"),
                Comparator.comparing(StockSummaryDTO::getPct));
    }

    /**
     * 4개 탭을 API 2회 호출로 한번에 반환.
     * fetchVolumeRank("0") 결과를 공유해 volume/topGainers/topLosers를 재정렬 — 중복 호출 제거.
     */
    public AllRankResult getAllRanks() {
        List<KisRankApiClient.RankItem> byValue  = kisRankApiClient.fetchVolumeRank("3");
        List<KisRankApiClient.RankItem> byVolume = kisRankApiClient.fetchVolumeRank("0");

        return new AllRankResult(
                toSortedDtoList(byValue,  Comparator.comparing(StockSummaryDTO::getValue).reversed()),
                toDtoList(byVolume),
                toSortedDtoList(byVolume, Comparator.comparing(StockSummaryDTO::getPct).reversed()),
                toSortedDtoList(byVolume, Comparator.comparing(StockSummaryDTO::getPct))
        );
    }

    @Getter
    @AllArgsConstructor
    public static class AllRankResult {
        private final List<StockSummaryDTO> tradeValue;
        private final List<StockSummaryDTO> volume;
        private final List<StockSummaryDTO> topGainers;
        private final List<StockSummaryDTO> topLosers;
    }

    private List<StockSummaryDTO> toDtoList(List<KisRankApiClient.RankItem> items) {
        preloadSectorsFromDb(items);
        return items.stream()
                .map(this::toDto)
                .limit(TOP_N)
                .collect(Collectors.toList());
    }

    private List<StockSummaryDTO> toSortedDtoList(
            List<KisRankApiClient.RankItem> items, Comparator<StockSummaryDTO> comparator) {

        preloadSectorsFromDb(items);
        return items.stream()
                .map(this::toDto)
                .sorted(comparator)
                .limit(TOP_N)
                .collect(Collectors.toList());
    }

    /** 캐시에 없는 코드만 stock_info DB에서 업종을 일괄 조회해 캐시에 채운다 (KIS 호출 없음) */
    private void preloadSectorsFromDb(List<KisRankApiClient.RankItem> items) {
        List<String> missing = items.stream()
                .map(KisRankApiClient.RankItem::getStockCode)
                .filter(code -> !sectorCache.containsKey(code))
                .distinct()
                .collect(Collectors.toList());
        if (missing.isEmpty()) return;

        for (StockInfo info : stockInfoRepository.findAllById(missing)) {
            if (info.getSector() != null && !info.getSector().isBlank()) {
                sectorCache.put(info.getStockCode(), info.getSector());
            }
        }
    }

    /**
     * DB에 업종이 없는 종목을 주기당 limit개씩만 KIS에서 적재 (rate limit 보호).
     * 랭크 브로드캐스트 스케줄러가 10초마다 호출 — 몇 분 안에 랭크 종목 전체가 채워지고
     * 이후에는 DB 캐시로만 동작한다.
     */
    public void backfillMissingSectors(Collection<String> codes, int limit) {
        int attempted = 0;
        for (String code : codes) {
            if (attempted >= limit) break;
            if (sectorCache.containsKey(code)) continue;
            attempted++;
            try {
                StockInfo info = stockInfoService.getOrFetch(code);
                String sector = info.getSector();
                // 업종이 비어 있어도 마커를 넣어 매 주기 재호출을 막는다
                sectorCache.put(code, sector != null ? sector : "");
            } catch (Exception e) {
                log.warn("업종 적재 실패 code={}: {}", code, e.getMessage());
            }
        }
    }

    /**
     * 지금 뜨는 산업 — 코스피 업종별(반도체/화학/건설 등) 등락률 순위.
     * KIS FHPUP02140000(국내업종 구분별전체시세)을 그대로 등락률 내림차순 최대 10개로 노출.
     */
    public List<Map<String, Object>> getIndustrySectorRanks() {
        List<KisRankApiClient.IndustryItem> items = kisRankApiClient.fetchIndustryRanks();

        List<Map<String, Object>> result = new ArrayList<>();
        for (KisRankApiClient.IndustryItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", item.getName());
            row.put("code", item.getCode());
            row.put("pct", item.getPct());
            result.add(row);
        }
        return result.size() > 10 ? result.subList(0, 10) : result;
    }

    /**
     * 투자자 동향 — 코스피 전체 개인/외국인/기관 순매수 거래대금(당일).
     * KIS FHPTJ04040000(시장별 투자자매매동향 일별): 이미 백만원 단위로 응답됨.
     */
    public List<Map<String, Object>> getMarketInvestorSentiment() {
        KisRankApiClient.InvestorSentimentItem data = kisRankApiClient.fetchMarketInvestorSentiment();

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(sentimentRow("개인",   data.getPersonalNetBuy()));
        result.add(sentimentRow("외국인", data.getForeignNetBuy()));
        result.add(sentimentRow("기관",   data.getInstitutionNetBuy()));
        return result;
    }

    private Map<String, Object> sentimentRow(String who, long valMillion) {
        long valEok = valMillion / 100; // KIS 필드는 백만원 단위 → 억원으로 변환 (Toss 등 타 서비스 기준)
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("who", who);
        m.put("val", valEok);
        m.put("buy", valEok >= 0);
        return m;
    }

    private StockSummaryDTO toDto(KisRankApiClient.RankItem item) {
        BigDecimal pct = item.getChangeRate() != null
                ? BigDecimal.valueOf(item.getChangeRate())
                : BigDecimal.ZERO;

        long valueInEok = item.getTradeValue() != null ? item.getTradeValue() / 1_000_000_000L : 0L;

        String cachedSector = sectorCache.get(item.getStockCode());
        String sector = cachedSector != null && !cachedSector.isBlank() ? cachedSector : null;

        String logoUrl = "https://file.alphasquare.co.kr/media/images/stock_logo/kr/" + item.getStockCode() + ".png";
        return new StockSummaryDTO(
                item.getStockCode(),
                item.getStockName(),
                item.getCurrentPrice(),
                pct,
                valueInEok,
                item.getVolume(),
                sector,
                logoUrl
        );
    }
}