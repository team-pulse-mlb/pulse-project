package com.pulse.common.message;

import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.ScoreTaskOutbox;
import com.pulse.domain.ScoreTaskOutboxRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ScoreTaskPublisher {

    private final ScoreTaskOutboxRepository outboxRepository;
    private final ScoreTaskOutboxDispatcher dispatcher;
    private final AfterCommitExecutor afterCommitExecutor;
    private final Clock clock;

    @Autowired
    public ScoreTaskPublisher(
            ScoreTaskOutboxRepository outboxRepository,
            ScoreTaskOutboxDispatcher dispatcher,
            AfterCommitExecutor afterCommitExecutor
    ) {
        this(outboxRepository, dispatcher, afterCommitExecutor, Clock.systemUTC());
    }

    ScoreTaskPublisher(
            ScoreTaskOutboxRepository outboxRepository,
            ScoreTaskOutboxDispatcher dispatcher,
            AfterCommitExecutor afterCommitExecutor,
            Clock clock
    ) {
        this.outboxRepository = outboxRepository;
        this.dispatcher = dispatcher;
        this.afterCommitExecutor = afterCommitExecutor;
        this.clock = clock;
    }

    @Transactional
    public void publish(ScoreTask task) {
        Objects.requireNonNull(task, "ScoreTask는 null일 수 없습니다.");
        Objects.requireNonNull(task.observedAt(), "ScoreTask observedAt은 null일 수 없습니다.");

        UUID outboxId = outboxRepository.findByGameIdAndObservedAt(task.gameId(), task.observedAt())
                .map(ScoreTaskOutbox::getOutboxId)
                .orElseGet(() -> savePending(task));
        afterCommitExecutor.execute(() -> dispatcher.publishTask(outboxId));
    }

    private UUID savePending(ScoreTask task) {
        ScoreTaskOutbox outbox = outboxRepository.save(ScoreTaskOutbox.pending(task, clock.instant()));
        return outbox.getOutboxId();
    }
}
