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
    private LocalDateTime updatedAt;

    /** 매수: 수량·평균단가 갱신 */
    public void buy(int qty, long price) {
        long totalCost = (long) this.quantity * this.avgBuyPrice + (long) qty * price;
        this.quantity += qty;
        this.avgBuyPrice = totalCost / this.quantity;
        this.updatedAt = LocalDateTime.now();
    }

    /** 매도: 수량 차감. 반환값이 true이면 전량 매도 → 레코드 삭제 필요 */
    public boolean sell(int qty) {
        this.quantity -= qty;
        this.updatedAt = LocalDateTime.now();
        return this.quantity <= 0;
    }
}
