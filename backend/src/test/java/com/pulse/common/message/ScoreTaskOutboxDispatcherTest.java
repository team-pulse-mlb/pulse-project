package com.pulse.common.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.config.RabbitMqConfig;
import com.pulse.domain.ScoreTaskOutbox;
import com.pulse.domain.ScoreTaskOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;

class ScoreTaskOutboxDispatcherTest {

    private final Instant now = Instant.parse("2026-07-15T00:00:00Z");
    private final ScoreTaskOutboxRepository repository = mock(ScoreTaskOutboxRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ScoreTaskOutboxProperties properties = new ScoreTaskOutboxProperties(
            Duration.ofSeconds(5),
            Duration.ofMinutes(5),
            50
    );

    @Test
    void publishTask_shouldMarkPublishedWhenRabbitMqAcceptsMessage() {
        ScoreTaskOutbox outbox = pendingTask();
        when(repository.findPendingByOutboxIdForUpdate(outbox.getOutboxId())).thenReturn(Optional.of(outbox));
        ScoreTaskOutboxDispatcher dispatcher = dispatcherAt(now);

        dispatcher.publishTask(outbox.getOutboxId());

        verify(rabbitTemplate).convertAndSend(RabbitMqConfig.SCORE_TASKS_QUEUE, outbox.getPayload());
        assertThat(outbox.getStatus()).isEqualTo(ScoreTaskOutbox.STATUS_PUBLISHED);
        assertThat(outbox.getPublishedAt()).isEqualTo(now);
    }

    @Test
    void publishTask_shouldKeepPendingAndScheduleRetryWhenRabbitMqFails() {
        ScoreTaskOutbox outbox = pendingTask();
        when(repository.findPendingByOutboxIdForUpdate(outbox.getOutboxId())).thenReturn(Optional.of(outbox));
        doThrow(new AmqpException("브로커 연결 실패"))
                .when(rabbitTemplate).convertAndSend(RabbitMqConfig.SCORE_TASKS_QUEUE, outbox.getPayload());

        dispatcherAt(now).publishTask(outbox.getOutboxId());

        assertThat(outbox.getStatus()).isEqualTo(ScoreTaskOutbox.STATUS_PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(5));
        assertThat(outbox.getLastError()).isEqualTo("브로커 연결 실패");
    }

    @Test
    void publishReady_shouldRecoverExactTaskAfterBrokerRestartWithoutLoss() {
        ScoreTaskOutbox outbox = pendingTask();
        when(repository.findPendingByOutboxIdForUpdate(outbox.getOutboxId())).thenReturn(Optional.of(outbox));
        doThrow(new AmqpException("브로커 중단"))
                .doNothing()
                .when(rabbitTemplate).convertAndSend(RabbitMqConfig.SCORE_TASKS_QUEUE, outbox.getPayload());

        dispatcherAt(now).publishTask(outbox.getOutboxId());

        Instant restartedAt = now.plusSeconds(5);
        when(repository.findReadyForUpdate(restartedAt, PageRequest.of(0, 50))).thenReturn(List.of(outbox));
        dispatcherAt(restartedAt).publishReady();

        ArgumentCaptor<ScoreTask> taskCaptor = ArgumentCaptor.forClass(ScoreTask.class);
        verify(rabbitTemplate, times(2)).convertAndSend(
                org.mockito.ArgumentMatchers.eq(RabbitMqConfig.SCORE_TASKS_QUEUE),
                taskCaptor.capture()
        );
        assertThat(taskCaptor.getAllValues()).containsOnly(outbox.getPayload());
        assertThat(outbox.getStatus()).isEqualTo(ScoreTaskOutbox.STATUS_PUBLISHED);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getPublishedAt()).isEqualTo(restartedAt);
    }

    private ScoreTaskOutboxDispatcher dispatcherAt(Instant instant) {
        return new ScoreTaskOutboxDispatcher(
                repository,
                rabbitTemplate,
                properties,
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }

    private ScoreTaskOutbox pendingTask() {
        return ScoreTaskOutbox.pending(new ScoreTask(
                100L,
                now.minusSeconds(10),
                12L,
                "LIVE",
                ScoreTask.Situation.of(2, 3, 2, true, true, false)
        ), now);
    }
}
