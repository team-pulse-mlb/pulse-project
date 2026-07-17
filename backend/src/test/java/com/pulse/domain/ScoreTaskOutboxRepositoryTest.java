package com.pulse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.common.message.ScoreTask;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("보존 기간이 지난 발행 완료 행만 지정한 배치 크기까지 삭제한다")
    void shouldDeleteOnlyPublishedRowsOlderThanRetentionPeriodWithinBatchLimit() {
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        ScoreTaskOutbox expired = ScoreTaskOutbox.pending(
                new ScoreTask(101L, now.minusSeconds(2), null, "LIVE", null),
                now.minusSeconds(2)
        );
        expired.markPublished(now.minusSeconds(2));
        ScoreTaskOutbox retained = ScoreTaskOutbox.pending(
                new ScoreTask(102L, now.minusSeconds(1), null, "LIVE", null),
                now.minusSeconds(1)
        );
        retained.markPublished(now.minusSeconds(1));
        repository.saveAndFlush(expired);
        repository.saveAndFlush(retained);

        int deleted = repository.deletePublishedBefore(now, 1);
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(expired.getOutboxId())).isEmpty();
        assertThat(repository.findById(retained.getOutboxId())).isPresent();
    }
}
