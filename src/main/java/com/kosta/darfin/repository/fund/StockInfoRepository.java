package com.kosta.darfin.repository.fund;

import com.kosta.darfin.entity.fund.StockInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface StockInfoRepository extends JpaRepository<StockInfo, String> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO stock_info " +
            "(stock_code, stock_name, market, sector, market_cap, per, pbr, updated_at) " +
            "VALUES (:stockCode, :stockName, :market, :sector, :marketCap, :per, :pbr, :updatedAt)",
            nativeQuery = true)
    void insertStockInfo(@Param("stockCode") String stockCode,
                         @Param("stockName") String stockName,
                         @Param("market") String market,
                         @Param("sector") String sector,
                         @Param("marketCap") Long marketCap,
                         @Param("per") Double per,
                         @Param("pbr") Double pbr,
                         @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Transactional
    @Query(value = "UPDATE stock_info SET " +
            "stock_name = :stockName, market = :market, sector = :sector, " +
            "market_cap = :marketCap, per = :per, pbr = :pbr, updated_at = :updatedAt " +
            "WHERE stock_code = :stockCode",
            nativeQuery = true)
    void updateStockInfo(@Param("stockCode") String stockCode,
                         @Param("stockName") String stockName,
                         @Param("market") String market,
                         @Param("sector") String sector,
                         @Param("marketCap") Long marketCap,
                         @Param("per") Double per,
                         @Param("pbr") Double pbr,
                         @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 공용 stock 테이블(DART 연동, company_name 보유)에서
     * 종목코드 기준으로 기업명만 조회.
     * stock_info Repository에 같이 둔 이유: 별도 Repository<Object,Long>로 만들면
     * Spring Data JPA가 "관리되는 엔티티가 아니다"라며 빈 생성에 실패하기 때문.
     */
    @Query(value = "SELECT company_name FROM stock WHERE stock_code = :stockCode LIMIT 1",
            nativeQuery = true)
    Optional<String> findCompanyNameByStockCode(@Param("stockCode") String stockCode);
}