package com.pulse.api.notification;

import com.pulse.common.message.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * NotificationEventListener 단위 테스트입니다.
 *
 * Listener의 역할은 RabbitMQ에서 받은 이벤트를
 * NotificationFanOutService에 그대로 전달하는 것입니다.
 *
 * 실제 대상 사용자 조회와 알림 저장 로직은
 * NotificationFanOutServiceTest에서 별도로 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    /**
     * 실제 Fan-out 서비스 대신 사용하는 Mock 객체입니다.
     */
    @Mock
    private NotificationFanOutService notificationFanOutService;

    /**
     * 위 Mock 서비스를 주입한 테스트 대상 Listener입니다.
     */
    @InjectMocks
    private NotificationEventListener notificationEventListener;

    /**
     * 정상 NotificationEvent를 수신하면
     * 동일한 이벤트가 Fan-out 서비스에 전달되는지 검증합니다.
     */
    @Test
    void handle_shouldDelegateEventToFanOutService() {
        // given
        NotificationEvent event = new NotificationEvent(
                UUID.randomUUID(),
                NotificationEvent.NotificationType.SURGE,
                100L,
                "경기 흐름이 급격하게 바뀌고 있습니다.",
                "SURGE",
                Instant.parse("2026-07-10T10:00:00Z")
        );

        // when
        notificationEventListener.handle(event);

        // then
        /*
         * Listener가 이벤트를 변경하거나 새로 만들지 않고
         * 받은 객체를 그대로 서비스에 전달했는지 확인합니다.
         */
        verify(notificationFanOutService)
                .fanOut(event);
    }

    /**
     * null 메시지를 받았을 때는
     * Fan-out 서비스를 호출하지 않는지 검증합니다.
     */
    @Test
    void handle_shouldSkipNullEvent() {
        // when
        notificationEventListener.handle(null);

        // then
        verify(notificationFanOutService, never())
                .fanOut(null);
    }
}