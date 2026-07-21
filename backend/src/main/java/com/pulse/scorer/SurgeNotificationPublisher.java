package com.pulse.scorer;

import com.pulse.common.message.NotificationEvent;
import com.pulse.common.message.NotificationEvent.NotificationType;
import com.pulse.common.message.NotificationEventPublisher;
import com.pulse.domain.NotificationEventLogRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 커밋 후 승인된 SURGE 알림을 독립 트랜잭션으로 저장한다. */
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SurgeNotificationPublisher {

    private final LatestTagSelector latestTagSelector;
    private final NotificationEventPublisher notificationEventPublisher;
    private final NotificationEventLogRepository notificationEventLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(
            long gameId,
            List<String> tags,
            List<String> previousTags,
            Instant occurredAt
    ) {
        UUID eventId = eventIdFor(gameId, occurredAt);
        if (notificationEventLogRepository.existsById(eventId)) {
            log.debug("이미 저장된 SURGE 알림 skip: gameId={} occurredAt={}", gameId, occurredAt);
            return;
        }

        String latestTag = latestTagSelector.select(tags, previousTags, null).tag();
        if (latestTag.isBlank()) {
            latestTag = "경기 흐름 변화";
        }
        notificationEventPublisher.publish(new NotificationEvent(
                eventId,
                NotificationType.SURGE,
                gameId,
                "지금 볼 만한 경기가 있어요 — " + latestTag,
                latestTag,
                occurredAt
        ), tags);
        log.info("SURGE 알림 발행 gameId={} latestTag={}", gameId, latestTag);
    }

    static UUID eventIdFor(long gameId, Instant occurredAt) {
        String idempotencyKey = gameId + ":" + occurredAt + ":" + NotificationType.SURGE;
        return UUID.nameUUIDFromBytes(idempotencyKey.getBytes(StandardCharsets.UTF_8));
    }
}
