package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.config.TransactionManagementConfigUtils;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * LiveScoreComputedEvent가 커밋 후에만 AFTER_COMMIT 리스너로 전달되고
 * 롤백 시 전달되지 않음을 트랜잭션 위상 수준에서 검증한다.
 * 이 위상 보장 위에서 PlayTranslationCommitListener 등 커밋 후 부수효과가 동작한다.
 */
class LiveScoreComputedEventDeliveryTest {

    @AfterEach
    void cleanUpTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void event_isDeliveredOnlyAfterCommit() {
        try (AnnotationConfigApplicationContext ctx = newContext()) {
            RecordingListener listener = ctx.getBean(RecordingListener.class);
            beginTransaction();

            ctx.publishEvent(sampleEvent());
            // 커밋 전에는 전달되지 않는다.
            assertThat(listener.received).isEmpty();

            completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
            assertThat(listener.received).hasSize(1);
            assertThat(listener.received.get(0).gameId()).isEqualTo(5059180L);
        }
    }

    @Test
    void event_isNotDeliveredWhenTransactionRollsBack() {
        try (AnnotationConfigApplicationContext ctx = newContext()) {
            RecordingListener listener = ctx.getBean(RecordingListener.class);
            beginTransaction();

            ctx.publishEvent(sampleEvent());
            completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

            assertThat(listener.received).isEmpty();
        }
    }

    private static AnnotationConfigApplicationContext newContext() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(
                TransactionManagementConfigUtils.TRANSACTIONAL_EVENT_LISTENER_FACTORY_BEAN_NAME,
                TransactionalEventListenerFactory.class);
        ctx.register(RecordingListener.class);
        ctx.refresh();
        return ctx;
    }

    private static LiveScoreComputedEvent sampleEvent() {
        return new LiveScoreComputedEvent(
                5059180L, Instant.parse("2026-07-17T01:00:00Z"),
                80.0, 80, 60, List.of("TAG"), List.of(),
                8, "TOP", 100L, null, "LIVE", 5);
    }

    private static void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void completeTransaction(int status) {
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                synchronization.afterCommit();
            }
            synchronization.afterCompletion(status);
        }
    }

    static class RecordingListener {
        final List<LiveScoreComputedEvent> received = new ArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void onEvent(LiveScoreComputedEvent event) {
            received.add(event);
        }
    }
}
