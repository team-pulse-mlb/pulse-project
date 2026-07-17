package com.pulse.scorer;

import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.poller.GameLifecycle;
import com.pulse.ranking.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** scorer 기동 시 DB의 최신 점수로 라이브 랭킹 캐시를 복원한다. */
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@Profile("!headline-backfill & !ai-copy-reprocess & !rescore & !replay & !backtest & !migration")
@RequiredArgsConstructor
@Slf4j
public class LiveRankingRebuildRunner {

    private final GameRepository gameRepository;
    private final WatchScoreRepository watchScoreRepository;
    private final RankingService rankingService;

    @EventListener(ApplicationReadyEvent.class)
    void rebuild() {
        try {
            int restoredCount = 0;
            for (Game game : gameRepository.findByLifecycleState(GameLifecycle.LIVE.name())) {
                WatchScore latestScore = watchScoreRepository
                        .findTopByGameIdOrderByComputedAtDesc(game.getId())
                        .orElse(null);
                if (latestScore == null || latestScore.getWatchScore() == null) {
                    continue;
                }
                rankingService.updateLive(game.getId(), latestScore.getWatchScore());
                restoredCount++;
            }
            log.info("라이브 랭킹 캐시 재구축 완료: {}경기", restoredCount);
        } catch (Exception exception) {
            log.error("라이브 랭킹 캐시 재구축 실패: 다음 점수 계산에서 자연 복구합니다", exception);
        }
    }
}
