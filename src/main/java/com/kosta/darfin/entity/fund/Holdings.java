package com.kosta.darfin.entity.fund;

import com.kosta.darfin.entity.common.Users;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "holdings")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holdings {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long holdingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private StockInfo stockInfo;

    private Integer quantity;
    private Long avgBuyPrice;
    private LocalDateTime firstBoughtAt; // 현재 포지션 최초 매수 시각 — trades.holdDays 계산용
    private LocalDateTime updatedAt;

    /** 최초 매수 시각 — 과거 데이터(컬럼 추가 전 생성 레코드)는 null이라 updatedAt으로 대체 */
    public LocalDateTime getFirstBoughtAtOrFallback() {
        return firstBoughtAt != null ? firstBoughtAt : updatedAt;
    }

    /** 매수: 수량·평균단가 갱신 (포트폴리오 서비스용) */
    public void buy(int qty, long price) {
        long totalCost = (long) this.quantity * this.avgBuyPrice + (long) qty * price;
        this.quantity += qty;
        this.avgBuyPrice = totalCost / this.quantity;
        this.updatedAt = LocalDateTime.now();
    }

    /** 매도: 수량 차감. true 반환 시 전량 매도 → 레코드 삭제 필요 (포트폴리오 서비스용) */
    public boolean sell(int qty) {
        this.quantity -= qty;
        this.updatedAt = LocalDateTime.now();
        return this.quantity <= 0;
    }

    /** 매수 후 평균가·수량 갱신 (주문 서비스용) */
    public void applyBuy(int addQty, long buyPrice) {
        long newAvg = ((long) this.quantity * this.avgBuyPrice + (long) addQty * buyPrice)
                / (this.quantity + addQty);
        this.quantity += addQty;
        this.avgBuyPrice = newAvg;
        this.updatedAt = LocalDateTime.now();
    }

    /** 매도 후 수량 차감 (주문 서비스용) */
    public void applySell(int sellQty) {
        this.quantity -= sellQty;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * FIFO 매도 후 잔여 로트 집계로 재동기화.
     * FIFO에서는 오래된(단가가 다른) 로트부터 빠지므로 잔여 평단·최초매수일이 달라진다.
     */
    public void applyFifoSell(int sellQty, Long remainingAvgPrice, LocalDateTime oldestRemainingBoughtAt) {
        this.quantity -= sellQty;
        if (remainingAvgPrice != null) this.avgBuyPrice = remainingAvgPrice;
        if (oldestRemainingBoughtAt != null) this.firstBoughtAt = oldestRemainingBoughtAt;
        this.updatedAt = LocalDateTime.now();
    }
}
