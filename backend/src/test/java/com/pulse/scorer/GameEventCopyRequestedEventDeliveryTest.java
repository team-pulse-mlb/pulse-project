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

/** AI 경기 이벤트 문구 요청이 DB 커밋 후에만 전달되는지 검증한다. */
class GameEventCopyRequestedEventDeliveryTest {

    @AfterEach
    void cleanUpTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void event_isDeliveredOnlyAfterCommit() {
        try (AnnotationConfigApplicationContext context = newContext()) {
            RecordingListener listener = context.getBean(RecordingListener.class);
            beginTransaction();

            context.publishEvent(sampleEvent());
            assertThat(listener.received).isEmpty();

            completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
            assertThat(listener.received).containsExactly(sampleEvent());
        }
    }

    @Test
    void event_isNotDeliveredWhenTransactionRollsBack() {
        try (AnnotationConfigApplicationContext context = newContext()) {
            RecordingListener listener = context.getBean(RecordingListener.class);
            beginTransaction();

            context.publishEvent(sampleEvent());
            completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

            assertThat(listener.received).isEmpty();
        }
    }

    private static AnnotationConfigApplicationContext newContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(
                TransactionManagementConfigUtils.TRANSACTIONAL_EVENT_LISTENER_FACTORY_BEAN_NAME,
                TransactionalEventListenerFactory.class);
        context.register(RecordingListener.class);
        context.refresh();
        return context;
    }

    private static GameEventCopyRequestedEvent sampleEvent() {
        return new GameEventCopyRequestedEvent(
                5059041L,
                91L,
                AiGenerationTrigger.MODE_PROTECTED,
                Instant.parse("2026-07-21T01:00:00Z"));
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
        final List<GameEventCopyRequestedEvent> received = new ArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void onEvent(GameEventCopyRequestedEvent event) {
            received.add(event);
        }
    }
}
