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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;

class ScoreTaskOutboxDispatcherTest {

    private final Instant now = Instant.parse("2026-07-15T00:00:00Z");
    private final ScoreTaskOutboxRepository repository = mock(ScoreTaskOutboxRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ScoreTaskOutboxProperties properties = new ScoreTaskOutboxProperties(
            Duration.ofSeconds(5),
            Duration.ofMinutes(5),
            50,
            Duration.ofSeconds(1),
            Duration.ofDays(7),
            500
    );

    @Test
    @DisplayName("브로커 ack를 받기 전에는 ScoreTask outbox를 발행 완료로 바꾸지 않는다")
    void publishTask_shouldMarkPublishedOnlyAfterBrokerAck() throws Exception {
        ScoreTaskOutbox outbox = pendingTask();
        when(repository.findPendingByOutboxIdForUpdate(outbox.getOutboxId())).thenReturn(Optional.of(outbox));
        ScoreTaskOutboxDispatcher dispatcher = dispatcherAt(now);
        AtomicReference<CorrelationData> sentCorrelation = new AtomicReference<>();
        CountDownLatch sent = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            sentCorrelation.set(invocation.getArgument(2));
            sent.countDown();
            return null;
        }).when(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(RabbitMqConfig.SCORE_TASKS_QUEUE),
                org.mockito.ArgumentMatchers.eq(outbox.getPayload()),
                org.mockito.ArgumentMatchers.any(CorrelationData.class)
        );

        CompletableFuture<Void> publishing = CompletableFuture.runAsync(
                () -> dispatcher.publishTask(outbox.getOutboxId())
        );

        assertThat(sent.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(ScoreTaskOutbox.STATUS_PENDING);

        sentCorrelation.get().getFuture().complete(new CorrelationData.Confirm(true, null));
        publishing.get(1, TimeUnit.SECONDS);

        assertThat(outbox.getStatus()).isEqualTo(ScoreTaskOutbox.STATUS_PUBLISHED);
        assertThat(outbox.getPublishedAt()).isEqualTo(now);
    }

    @Test
    void publishTask_shouldKeepPendingAndScheduleRetryWhenRabbitMqFails() {
        ScoreTaskOutbox outbox = pendingTask();
        when(repository.findPendingByOutboxIdForUpdate(outbox.getOutboxId())).thenReturn(Optional.of(outbox));
        doThrow(new AmqpException("브로커 연결 실패"))
                .when(rabbitTemplate).convertAndSend(
                        org.mockito.ArgumentMatchers.eq(RabbitMqConfig.SCORE_TASKS_QUEUE),
                        org.mockito.ArgumentMatchers.eq(outbox.getPayload()),
                        org.mockito.ArgumentMatchers.any(CorrelationData.class)
                );

        dispatcherAt(now).publishTask(outbox.getOutboxId());

        assertThat(outbox.getStatus()).isEqualTo(ScoreTaskOutbox.STATUS_PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(5));
        assertThat(outbox.getLastError()).isEqualTo("브로커 연결 실패");
    }

    @Test
    @DisplayName("브로커 nack이면 ScoreTask outbox를 대기 상태로 유지하고 재시도를 예약한다")
    void publishTask_shouldKeepPendingAndScheduleRetryWhenBrokerNacks() {
        ScoreTaskOutbox outbox = pendingTask();
        when(repository.findPendingByOutboxIdForUpdate(outbox.getOutboxId())).thenReturn(Optional.of(outbox));
        org.mockito.Mockito.doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(2);
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "큐 저장 실패"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(RabbitMqConfig.SCORE_TASKS_QUEUE),
                org.mockito.ArgumentMatchers.eq(outbox.getPayload()),
                org.mockito.ArgumentMatchers.any(CorrelationData.class)
        );

        dispatcherAt(now).publishTask(outbox.getOutboxId());

        assertThat(outbox.getStatus()).isEqualTo(ScoreTaskOutbox.STATUS_PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getLastError()).contains("큐 저장 실패");
    }

    @Test
    @DisplayName("publisher confirm 시간이 초과되면 ScoreTask outbox를 대기 상태로 유지한다")
    void publishTask_shouldKeepPendingAndScheduleRetryWhenConfirmTimesOut() {
        ScoreTaskOutbox outbox = pendingTask();
        when(repository.findPendingByOutboxIdForUpdate(outbox.getOutboxId())).thenReturn(Optional.of(outbox));
        ScoreTaskOutboxProperties shortTimeoutProperties = new ScoreTaskOutboxProperties(
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                50,
                Duration.ofMillis(5),
                Duration.ofDays(7),
                500
        );

        new ScoreTaskOutboxDispatcher(
                repository,
                rabbitTemplate,
                shortTimeoutProperties,
                Clock.fixed(now, ZoneOffset.UTC)
        ).publishTask(outbox.getOutboxId());

        assertThat(outbox.getStatus()).isEqualTo(ScoreTaskOutbox.STATUS_PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(5));
        assertThat(outbox.getLastError()).isEqualTo("TimeoutException");
    }

    @Test
    void publishReady_shouldRecoverExactTaskAfterBrokerRestartWithoutLoss() {
        ScoreTaskOutbox outbox = pendingTask();
        when(repository.findPendingByOutboxIdForUpdate(outbox.getOutboxId())).thenReturn(Optional.of(outbox));
        doThrow(new AmqpException("브로커 중단"))
                .doAnswer(invocation -> {
                    CorrelationData correlationData = invocation.getArgument(2);
                    correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
                    return null;
                })
                .when(rabbitTemplate).convertAndSend(
                        org.mockito.ArgumentMatchers.eq(RabbitMqConfig.SCORE_TASKS_QUEUE),
                        org.mockito.ArgumentMatchers.eq(outbox.getPayload()),
                        org.mockito.ArgumentMatchers.any(CorrelationData.class)
                );

        dispatcherAt(now).publishTask(outbox.getOutboxId());

        Instant restartedAt = now.plusSeconds(5);
        when(repository.findReadyForUpdate(restartedAt, PageRequest.of(0, 50))).thenReturn(List.of(outbox));
        dispatcherAt(restartedAt).publishReady();

        ArgumentCaptor<ScoreTask> taskCaptor = ArgumentCaptor.forClass(ScoreTask.class);
        verify(rabbitTemplate, times(2)).convertAndSend(
                org.mockito.ArgumentMatchers.eq(RabbitMqConfig.SCORE_TASKS_QUEUE),
                taskCaptor.capture(),
                org.mockito.ArgumentMatchers.any(CorrelationData.class)
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
