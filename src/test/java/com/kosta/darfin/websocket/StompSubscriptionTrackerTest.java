package com.kosta.darfin.websocket;

import com.kosta.darfin.service.fund.KisRealtimeClient;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StompSubscriptionTrackerTest {

    @Test
    void keepsDetailSubscriptionUntilBothOrderBookAndExecutionAreUnsubscribed() {
        KisRealtimeClient kisRealtimeClient = mock(KisRealtimeClient.class);
        StompSubscriptionTracker tracker = new StompSubscriptionTracker(
                kisRealtimeClient, mock(SimpMessagingTemplate.class));

        tracker.handleSubscribe(new SessionSubscribeEvent(this,
                stompMessage(StompCommand.SUBSCRIBE, "session-1", "orderbook-sub",
                        "/topic/detail/005930/orderbook")));
        tracker.handleSubscribe(new SessionSubscribeEvent(this,
                stompMessage(StompCommand.SUBSCRIBE, "session-1", "execution-sub",
                        "/topic/detail/005930/execution")));

        verify(kisRealtimeClient, times(1)).addDetailCode("005930");
        clearInvocations(kisRealtimeClient);

        tracker.handleUnsubscribe(new SessionUnsubscribeEvent(this,
                stompMessage(StompCommand.UNSUBSCRIBE, "session-1", "orderbook-sub", null)));
        verify(kisRealtimeClient, never()).removeDetailCode("005930");

        tracker.handleUnsubscribe(new SessionUnsubscribeEvent(this,
                stompMessage(StompCommand.UNSUBSCRIBE, "session-1", "execution-sub", null)));
        verify(kisRealtimeClient, times(1)).removeDetailCode("005930");
    }

    private Message<byte[]> stompMessage(StompCommand command,
                                         String sessionId,
                                         String subscriptionId,
                                         String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
