package com.kosta.darfin.websocket;

import com.kosta.darfin.service.fund.StockRankBroadcastScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 클라이언트가 /app/rank 를 구독하는 순간(SUBSCRIBE 프레임)에만 1회 응답 —
 * 캐시된 최신 랭크 payload가 있으면 그 세션에게만 즉시 전송한다.
 * 이후 실시간 갱신은 /topic/rank 브로드캐스트로 별도 수신한다.
 */
@Controller
@RequiredArgsConstructor
public class RankSnapshotController {

    private final StockRankBroadcastScheduler rankBroadcastScheduler;

    @SubscribeMapping("/rank")
    public Map<String, Object> rankSnapshot() {
        return rankBroadcastScheduler.getLastPayload();
    }
}
