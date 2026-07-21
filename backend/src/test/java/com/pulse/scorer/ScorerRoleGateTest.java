package com.pulse.scorer;

import com.pulse.scoring.ImportanceCalculator;
import com.pulse.scoring.ScoreCalculator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.NotificationEventPublisher;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.GameRepository;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.NotificationEventLogRepository;
import com.pulse.domain.OddsSnapshotRepository;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.PlayerSeasonStatRepository;
import com.pulse.domain.StandingRepository;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.ranking.RankingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

class ScorerRoleGateTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ScoreTaskListener.class,
                    LiveScoringService.class,
                    PregameScoringService.class,
                    GameFinalizationService.class,
                    SurgeDetector.class,
                    GameEventExtractor.class,
                    TimelineHighlightTrigger.class,
                    TimelineHighlightBackfill.class,
                    LiveRankingRebuildRunner.class,
                    LatestTagSelector.class,
                    GameEventCopyCommitListener.class,
                    SurgeCommitListener.class,
                    SurgeNotificationPublisher.class,
                    LiveSignalPublisher.class,
                    ImportanceCalculator.class)
            .withBean(GameRepository.class, () -> mock(GameRepository.class))
            .withBean(PlayRepository.class, () -> mock(PlayRepository.class))
            .withBean(WatchScoreRepository.class, () -> mock(WatchScoreRepository.class))
            .withBean(ScoreCalculator.class, () -> mock(ScoreCalculator.class))
            .withBean(GameEventRepository.class, () -> mock(GameEventRepository.class))
            .withBean(LineupRepository.class, () -> mock(LineupRepository.class))
            .withBean(OddsSnapshotRepository.class, () -> mock(OddsSnapshotRepository.class))
            .withBean(StandingRepository.class, () -> mock(StandingRepository.class))
            .withBean(PlayerSeasonStatRepository.class, () -> mock(PlayerSeasonStatRepository.class))
            .withBean(NotificationEventLogRepository.class, () -> mock(NotificationEventLogRepository.class))
            .withBean(NotificationEventPublisher.class, () -> mock(NotificationEventPublisher.class))
            .withBean(ScoringProperties.class, () -> mock(ScoringProperties.class))
            .withBean(AiGenerationTrigger.class, () -> mock(AiGenerationTrigger.class))
            .withBean(AfterCommitExecutor.class, () -> mock(AfterCommitExecutor.class))
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(RankingService.class, () -> mock(RankingService.class));

    @Test
    @DisplayName("scorer가 활성화되면 운영 scorer 체인 빈을 등록한다")
    void shouldRegisterScorerBeansWhenEnabled() {
        contextRunner.withPropertyValues("pulse.scorer.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ScoreTaskListener.class);
                    assertThat(context).hasSingleBean(LiveScoringService.class);
                    assertThat(context).hasSingleBean(PregameScoringService.class);
                    assertThat(context).hasSingleBean(GameFinalizationService.class);
                    assertThat(context).hasSingleBean(SurgeDetector.class);
                    assertThat(context).hasSingleBean(GameEventExtractor.class);
                    assertThat(context).hasSingleBean(TimelineHighlightBackfill.class);
                    assertThat(context).hasSingleBean(LiveRankingRebuildRunner.class);
                    assertThat(context).hasSingleBean(LatestTagSelector.class);
                    assertThat(context).hasSingleBean(GameEventCopyCommitListener.class);
                    assertThat(context).hasSingleBean(SurgeCommitListener.class);
                    assertThat(context).hasSingleBean(SurgeNotificationPublisher.class);
                    assertThat(context).hasSingleBean(LiveSignalPublisher.class);
                    assertThat(context).hasSingleBean(ImportanceCalculator.class);
                });
    }

    @Test
    @DisplayName("scorer가 비활성화되면 운영 scorer 체인 빈을 등록하지 않는다")
    void shouldNotRegisterScorerBeansWhenDisabled() {
        contextRunner.withPropertyValues("pulse.scorer.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ScoreTaskListener.class);
                    assertThat(context).doesNotHaveBean(LiveScoringService.class);
                    assertThat(context).doesNotHaveBean(PregameScoringService.class);
                    assertThat(context).doesNotHaveBean(GameFinalizationService.class);
                    assertThat(context).doesNotHaveBean(SurgeDetector.class);
                    assertThat(context).doesNotHaveBean(GameEventExtractor.class);
                    assertThat(context).doesNotHaveBean(TimelineHighlightBackfill.class);
                    assertThat(context).doesNotHaveBean(LiveRankingRebuildRunner.class);
                    assertThat(context).doesNotHaveBean(LatestTagSelector.class);
                    assertThat(context).doesNotHaveBean(GameEventCopyCommitListener.class);
                    assertThat(context).doesNotHaveBean(SurgeCommitListener.class);
                    assertThat(context).doesNotHaveBean(SurgeNotificationPublisher.class);
                    assertThat(context).doesNotHaveBean(LiveSignalPublisher.class);
                    assertThat(context).doesNotHaveBean(ImportanceCalculator.class);
                });
    }

    @Test
    @DisplayName("일회성 배치 프로파일에서는 라이브 랭킹 재구축 빈을 등록하지 않는다")
    void shouldNotRegisterLiveRankingRebuildRunnerForBatchProfile() {
        contextRunner.withPropertyValues(
                        "pulse.scorer.enabled=true",
                        "spring.profiles.active=headline-backfill")
                .run(context -> assertThat(context).doesNotHaveBean(LiveRankingRebuildRunner.class));
    }
}
