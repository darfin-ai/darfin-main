package com.kosta.darfin.service.fund;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KisRealtimeClientTest {

    @Test
    @SuppressWarnings("unchecked")
    void remembersOrderBookSubscriptionWhileWebSocketIsConnecting() {
        KisRealtimeClient client = new KisRealtimeClient(mock(SimpMessagingTemplate.class));
        ReflectionTestUtils.setField(client, "realtimeEnabled", true);

        client.addDetailCode("005930");

        Set<String> detailCodes = (Set<String>) ReflectionTestUtils.getField(client, "detailCodes");
        Set<String> executionCodes = (Set<String>) ReflectionTestUtils.getField(client, "subscribedCodes");
        Set<String> orderBookCodes = (Set<String>) ReflectionTestUtils.getField(client, "aspSubscribedCodes");
        assertThat(detailCodes).contains("005930");
        assertThat(executionCodes).contains("005930");
        assertThat(orderBookCodes).contains("005930");
    }

    @Test
    @SuppressWarnings("unchecked")
    void parsesH0STASP0PricesAndQuantitiesFromTheirActualPositions() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        KisRealtimeClient client = new KisRealtimeClient(messagingTemplate);

        String[] fields = new String[59];
        Arrays.fill(fields, "0");
        fields[0] = "005930";

        for (int i = 0; i < 10; i++) {
            fields[3 + i] = String.valueOf(80_000 - (i * 100));
            fields[13 + i] = String.valueOf(79_900 - (i * 100));
            fields[23 + i] = String.valueOf(1_000 + i);
            fields[33 + i] = String.valueOf(2_000 + i);
        }

        String message = "0|H0STASP0|001|" + String.join("^", fields);
        ReflectionTestUtils.invokeMethod(client, "handleRealtimeData", message);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/detail/005930/orderbook"), payloadCaptor.capture());

        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        List<Map<String, Object>> asks = (List<Map<String, Object>>) payload.get("asks");
        List<Map<String, Object>> bids = (List<Map<String, Object>>) payload.get("bids");

        assertThat(asks).hasSize(5);
        assertThat(asks.get(0)).containsEntry("price", 80_000L).containsEntry("quantity", 1_000L);
        assertThat(asks.get(4)).containsEntry("price", 79_600L).containsEntry("quantity", 1_004L);
        assertThat(bids).hasSize(5);
        assertThat(bids.get(0)).containsEntry("price", 79_900L).containsEntry("quantity", 2_000L);
        assertThat(bids.get(4)).containsEntry("price", 79_500L).containsEntry("quantity", 2_004L);
    }
}
