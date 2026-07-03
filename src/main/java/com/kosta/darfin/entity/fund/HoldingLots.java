package com.kosta.darfin.entity.fund;

import com.kosta.darfin.entity.common.Users;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * FIFO 로트 — 매수 1건당 1로트. 매도 시 boughtAt이 오래된 로트부터 소진하며
 * 로트별 매수가로 정확한 실현손익·보유일수를 계산한다.
 * Holdings는 화면 표시용 집계(총수량·평단), 이 테이블이 손익 계산의 원천.
 */
@Entity
@Table(name = "holding_lots")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HoldingLots {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_code", nullable = false)
    private StockInfo stockInfo;

    private Integer remainingQuantity; // 아직 매도되지 않은 잔여 수량
    private Long buyPrice;             // 이 로트의 매수 단가
    private LocalDateTime boughtAt;

    public void consume(int qty) {
        this.remainingQuantity -= qty;
    }
}
