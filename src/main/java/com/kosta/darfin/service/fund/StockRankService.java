package com.kosta.darfin.service.fund;

import com.kosta.darfin.dto.fund.StockSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
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
        return items.stream()
                .map(this::toDto)
                .limit(TOP_N)
                .collect(Collectors.toList());
    }

    private List<StockSummaryDTO> toSortedDtoList(
            List<KisRankApiClient.RankItem> items, Comparator<StockSummaryDTO> comparator) {

        return items.stream()
                .map(this::toDto)
                .sorted(comparator)
                .limit(TOP_N)
                .collect(Collectors.toList());
    }

    private StockSummaryDTO toDto(KisRankApiClient.RankItem item) {
        BigDecimal pct = item.getChangeRate() != null
                ? BigDecimal.valueOf(item.getChangeRate())
                : BigDecimal.ZERO;

        long valueInEok = item.getTradeValue() != null ? item.getTradeValue() / 1_000_000_000L : 0L;

        String logoUrl = "https://file.alphasquare.co.kr/media/images/stock_logo/kr/" + item.getStockCode() + ".png";
        return new StockSummaryDTO(
                item.getStockCode(),
                item.getStockName(),
                item.getCurrentPrice(),
                pct,
                valueInEok,
                logoUrl
        );
    }
}