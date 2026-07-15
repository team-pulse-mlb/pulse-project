package com.pulse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.common.message.ScoreTask;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = "spring.flyway.enabled=false")
class ScoreTaskOutboxRepositoryTest {

    @Autowired
    private ScoreTaskOutboxRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistAndRestoreOriginalScoreTaskPayload() {
        Instant observedAt = Instant.parse("2026-07-15T00:00:00Z");
        ScoreTask task = new ScoreTask(
                100L,
                observedAt,
                12L,
                "LIVE",
                ScoreTask.Situation.of(2, 3, 2, true, true, false)
        );
        ScoreTaskOutbox saved = repository.saveAndFlush(ScoreTaskOutbox.pending(task, observedAt));
        entityManager.clear();

        ScoreTaskOutbox restored = repository.findById(saved.getOutboxId()).orElseThrow();

        assertThat(restored.getPayload()).isEqualTo(task);
        assertThat(restored.getStatus()).isEqualTo(ScoreTaskOutbox.STATUS_PENDING);
        assertThat(restored.getGameId()).isEqualTo(task.gameId());
        assertThat(restored.getObservedAt()).isEqualTo(task.observedAt());
    }
}
