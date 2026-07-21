package com.pulse.api.notification;

import com.pulse.common.config.RabbitMqConfig;
import com.pulse.common.message.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ의 notify.events 큐에서 알림 이벤트를 수신하는 Consumer입니다.
 *
 * 이 클래스의 역할은 최대한 단순하게 유지합니다.
 *
 * 처리 순서:
 * 1. RabbitMQ에서 NotificationEvent 수신
 * 2. NotificationFanOutService에 이벤트 전달
 * 3. 서비스가 대상 사용자 조회 및 사용자별 알림 저장
 *
 * 실제 비즈니스 로직을 Listener가 아닌 Service에 둔 이유:
 * - 메시지 수신과 비즈니스 처리를 분리할 수 있음
 * - RabbitMQ 없이 Service만 단위 테스트하기 쉬움
 * - Listener 코드가 복잡해지는 것을 방지
 */
@Component
/**
 * 이 Listener는 pulse.notification.consumer-enabled=true인
 * API 역할에서만 생성됩니다.
 *
 * poller와 scorer가 같은 notify.events 큐를 경쟁 소비하지 않도록
 * 역할별 실행 조건을 명시적으로 제한합니다.
 */
@ConditionalOnProperty(
        prefix = "pulse.notification",
        name = "consumer-enabled",
        havingValue = "true"
)

@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    /**
     * 알림 유형에 따라 대상 사용자를 조회하고,
     * user_notifications 테이블에 사용자별 알림을 저장하는 서비스입니다.
     */
    private final NotificationFanOutService notificationFanOutService;

    /**
     * notify.events 큐에서 NotificationEvent를 수신합니다.
     *
     * 정상 처리:
     * - fanOut()이 예외 없이 종료
     * - RabbitMQ에 ACK 처리
     * - 메시지가 큐에서 제거
     *
     * 처리 실패:
     * - fanOut()에서 발생한 예외가 Listener 밖으로 전달
     * - RabbitMQ가 메시지를 재전달
     * - 설정된 전달 횟수를 초과하면 DLQ로 이동
     *
     * 중요:
     * 이 메서드에서 try-catch로 예외를 잡고 로그만 남기면 안 됩니다.
     *
     * 예외를 잡아서 정상 종료하면 RabbitMQ는 메시지 처리가 성공했다고
     * 판단하기 때문에 재전달과 DLQ 처리가 동작하지 않습니다.
     *
     * @param event RabbitMQ에서 역직렬화된 알림 이벤트
     */
    @RabbitListener(queues = RabbitMqConfig.NOTIFY_EVENTS_QUEUE)
    public void handle(NotificationEvent event) {
        /*
         * 정상적인 메시지라면 null이 전달될 가능성은 낮습니다.
         *
         * 방어적으로 null 메시지는 비즈니스 처리를 하지 않고 종료합니다.
         */
        if (event == null) {
            log.warn("빈 NotificationEvent를 수신하여 처리를 건너뜁니다.");
            return;
        }

        log.debug(
                "알림 이벤트 수신: eventId={}, type={}, gameId={}",
                event.eventId(),
                event.type(),
                event.gameId()
        );

        /*
         * 예외를 잡지 않고 그대로 Listener 컨테이너까지 전달합니다.
         *
         * 원본 이벤트 조회 실패, 경기 조회 실패, DB 저장 실패 등이 발생하면
         * RabbitMQ 재전달 및 DLQ 정책이 적용됩니다.
         */
        notificationFanOutService.fanOut(event);
    }
}