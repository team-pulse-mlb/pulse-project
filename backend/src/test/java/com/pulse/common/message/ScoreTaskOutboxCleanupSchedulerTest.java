package com.pulse.common.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.domain.ScoreTaskOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScoreTaskOutboxCleanupSchedulerTest {

    @Test
    @DisplayName("보존 기간이 지난 발행 완료 ScoreTask outbox를 LIMIT 배치로 삭제한다")
    void cleanupPublished_shouldDeleteOneLimitedBatchOlderThanRetentionPeriod() {
        Instant now = Instant.parse("2026-07-17T00:00:00Z");
        ScoreTaskOutboxRepository repository = mock(ScoreTaskOutboxRepository.class);
        ScoreTaskOutboxProperties properties = new ScoreTaskOutboxProperties(
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                50,
                Duration.ofSeconds(10),
                Duration.ofDays(7),
                300
        );
        when(repository.deletePublishedBefore(now.minus(Duration.ofDays(7)), 300)).thenReturn(300);
        ScoreTaskOutboxCleanupScheduler scheduler = new ScoreTaskOutboxCleanupScheduler(
                repository,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        int deleted = scheduler.cleanupPublished();

        assertThat(deleted).isEqualTo(300);
        verify(repository).deletePublishedBefore(now.minus(Duration.ofDays(7)), 300);
    }
}
