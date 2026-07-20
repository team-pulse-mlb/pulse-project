package com.pulse.common.message;

import com.pulse.domain.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 보존 기간이 지난 발행 완료 알림 outbox를 제한된 배치로 정리한다. */
@Component
@Slf4j
@ConditionalOnExpression("(${pulse.poller.enabled:false} or ${pulse.scorer.enabled:true})"
        + " and ${pulse.notification-outbox.cleanup-enabled:true}")
public class NotificationOutboxCleanupScheduler {

    private final NotificationOutboxRepository repository;
    private final NotificationOutboxProperties properties;
    private final Clock clock;

    @Autowired
    public NotificationOutboxCleanupScheduler(
            NotificationOutboxRepository repository,
            NotificationOutboxProperties properties
    ) {
        this(repository, properties, Clock.systemUTC());
    }

    NotificationOutboxCleanupScheduler(
            NotificationOutboxRepository repository,
            NotificationOutboxProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${pulse.notification-outbox.cleanup-delay:1h}")
    @Transactional
    public int cleanupPublished() {
        Instant cutoff = clock.instant().minus(properties.retentionPeriod());
        int deleted = repository.deletePublishedBefore(cutoff, properties.cleanupBatchSize());
        if (deleted > 0) {
            log.info("알림 outbox 정리 완료: cutoff={} deleted={}", cutoff, deleted);
        }
        return deleted;
    }
}
