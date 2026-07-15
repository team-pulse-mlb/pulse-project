package com.pulse.common.message;

import com.pulse.common.config.RabbitMqConfig;
import com.pulse.common.metrics.PulseMetrics;
import com.pulse.domain.NotificationOutbox;
import com.pulse.domain.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 미발행 알림을 RabbitMQ로 보내고 실패 건의 다음 재시도를 예약한다. */
@Component
@Slf4j
public class NotificationOutboxDispatcher {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final NotificationOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final NotificationOutboxProperties properties;
    private final Clock clock;

    @Autowired
    public NotificationOutboxDispatcher(
            NotificationOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            NotificationOutboxProperties properties
    ) {
        this(repository, rabbitTemplate, properties, Clock.systemUTC());
    }

    NotificationOutboxDispatcher(
            NotificationOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            NotificationOutboxProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void publishEvent(UUID eventId) {
        repository.findPendingByEventIdForUpdate(eventId).ifPresent(this::publish);
    }

    @Transactional
    public void publishReady() {
        Instant now = clock.instant();
        List<NotificationOutbox> ready = repository.findReadyForUpdate(
                now,
                PageRequest.of(0, properties.batchSize())
        );
        if (!ready.isEmpty()) {
            PulseMetrics.increment("pulse.notification.outbox.republish.runs");
        }
        ready.forEach(this::publish);
    }

    private void publish(NotificationOutbox outbox) {
        Instant now = clock.instant();
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.NOTIFY_EVENTS_QUEUE, outbox.toEvent());
            outbox.markPublished(now);
            log.info("알림 outbox 발행 성공: eventId={} attempts={}", outbox.getEventId(), outbox.getAttemptCount());
        } catch (RuntimeException exception) {
            PulseMetrics.increment("pulse.notification.publish.failures");
            Duration delay = retryDelay(outbox.getAttemptCount());
            outbox.recordFailure(now.plus(delay), errorMessage(exception));
            log.warn("알림 outbox 발행 실패: eventId={} nextAttemptAt={}",
                    outbox.getEventId(), outbox.getNextAttemptAt(), exception);
        }
    }

    private Duration retryDelay(int attemptCount) {
        int exponent = Math.min(attemptCount, 30);
        long multiplier = 1L << exponent;
        Duration calculated;
        try {
            calculated = properties.retryInitialInterval().multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return properties.retryMaxInterval();
        }
        return calculated.compareTo(properties.retryMaxInterval()) > 0
                ? properties.retryMaxInterval()
                : calculated;
    }

    private String errorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
