package com.kosta.darfin.service.fund;

import com.kosta.darfin.websocket.StockWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class StockDetailService {

    private final StockWebSocketHandler stockWebSocketHandler;
    private final KisRealtimeClient kisRealtimeClient;

    @PostConstruct
    public void init() {
        stockWebSocketHandler.setOnDetailSubscribeCallback(kisRealtimeClient::addDetailCode);
        stockWebSocketHandler.setOnDetailUnsubscribeCallback(kisRealtimeClient::removeDetailCode);
    }
}
