package com.pulse.common.message;

import com.pulse.common.config.RabbitMqConfig;
import com.pulse.common.metrics.PulseMetrics;
import com.pulse.domain.ScoreTaskOutbox;
import com.pulse.domain.ScoreTaskOutboxRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 미발행 ScoreTask를 RabbitMQ로 보내고 실패 건의 다음 재시도를 예약한다. */
@Component
@Slf4j
public class ScoreTaskOutboxDispatcher {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final ScoreTaskOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ScoreTaskOutboxProperties properties;
    private final Clock clock;

    @Autowired
    public ScoreTaskOutboxDispatcher(
            ScoreTaskOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            ScoreTaskOutboxProperties properties
    ) {
        this(repository, rabbitTemplate, properties, Clock.systemUTC());
    }

    ScoreTaskOutboxDispatcher(
            ScoreTaskOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            ScoreTaskOutboxProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishTask(UUID outboxId) {
        repository.findPendingByOutboxIdForUpdate(outboxId).ifPresent(this::publish);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishReady() {
        Instant now = clock.instant();
        List<ScoreTaskOutbox> ready = repository.findReadyForUpdate(
                now,
                PageRequest.of(0, properties.batchSize())
        );
        if (!ready.isEmpty()) {
            PulseMetrics.increment("pulse.score.task.outbox.republish.runs");
        }
        ready.forEach(this::publish);
    }

    private void publish(ScoreTaskOutbox outbox) {
        Instant now = clock.instant();
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.SCORE_TASKS_QUEUE, outbox.getPayload());
            outbox.markPublished(now);
            log.info("ScoreTask outbox 발행 성공: gameId={} observedAt={} attempts={}",
                    outbox.getGameId(), outbox.getObservedAt(), outbox.getAttemptCount());
        } catch (RuntimeException exception) {
            PulseMetrics.increment("pulse.score.task.publish.failures");
            Duration delay = retryDelay(outbox.getAttemptCount());
            outbox.recordFailure(now.plus(delay), errorMessage(exception));
            log.warn("ScoreTask outbox 발행 실패: gameId={} observedAt={} nextAttemptAt={}",
                    outbox.getGameId(), outbox.getObservedAt(), outbox.getNextAttemptAt(), exception);
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
