package com.pulse.common.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitExecutorTest {

    private final AfterCommitExecutor executor = new AfterCommitExecutor();

    @AfterEach
    void cleanUp() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void execute_shouldDeferActionUntilCommitWhenTransactionIsActive() {
        AtomicBoolean executed = new AtomicBoolean();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        executor.execute(() -> executed.set(true));

        assertThat(executed).isFalse();
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertThat(executed).isTrue();
    }

    @Test
    void execute_shouldRunImmediatelyWithoutTransaction() {
        AtomicBoolean executed = new AtomicBoolean();

        executor.execute(() -> executed.set(true));

        assertThat(executed).isTrue();
    }
}
