package com.pulse.api.notification;

import com.pulse.common.message.RedisSignalChannels;
import com.pulse.common.transaction.AfterCommitExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 사용자 알림 생성 사실을 Redis Pub/Sub으로 발행하는 컴포넌트입니다.
 *
 * Redis 메시지에는 알림 전체 내용을 넣지 않습니다.
 *
 * 채널:
 * signal:notification:{userId}
 *
 * 메시지:
 * notificationId
 *
 * 예:
 *
 * 채널  : signal:notification:7
 * 메시지: 501
 *
 * SSE 서버는 이 신호를 받은 뒤 사용자 7의 연결에만
 * notification_created 이벤트를 전송합니다.
 */
@Component
@RequiredArgsConstructor
public class NotificationSignalPublisher {

    /**
     * Redis Pub/Sub 메시지를 발행하는 Spring 도구입니다.
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 현재 DB 트랜잭션이 정상적으로 커밋된 후에만
     * 외부 메시지를 발행하도록 실행 순서를 보장합니다.
     */
    private final AfterCommitExecutor afterCommitExecutor;

    /**
     * 특정 사용자의 알림이 새로 생성됐다는 신호를 예약합니다.
     *
     * 이 메서드를 트랜잭션 내부에서 호출해도 즉시 Redis에 보내지 않고,
     * DB COMMIT이 성공한 뒤 실제 메시지가 발행됩니다.
     *
     * @param userId 알림 대상 사용자 ID
     * @param notificationId 새로 생성된 user_notifications PK
     */
    public void publishCreated(
            long userId,
            long notificationId
    ) {
        afterCommitExecutor.execute(
                () -> publishCreatedNow(
                        userId,
                        notificationId
                )
        );
    }

    /**
     * Redis에 알림 생성 신호를 실제로 발행합니다.
     */
    private void publishCreatedNow(
            long userId,
            long notificationId
    ) {
        redisTemplate.convertAndSend(
                RedisSignalChannels.notificationChannel(userId),
                String.valueOf(notificationId)
        );
    }
}