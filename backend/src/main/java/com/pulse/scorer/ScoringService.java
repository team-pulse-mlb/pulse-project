package com.pulse.scorer;

import com.pulse.common.config.ScoringProperties;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * watch_score 계산 파이프라인. 계산 결과는 watch_scores에 로그로 남기고
 * Redis 랭킹을 갱신한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoringService {

    private final GameRepository gameRepository;
    private final PlayRepository playRepository;
    private final WatchScoreRepository watchScoreRepository;
    private final ScoreCalculator calculator;
    private final RankingService rankingService;
    private final ScoringProperties props;

    @Transactional
    public void recalculate(long gameId) {
        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null) {
            log.warn("score task for unknown game: {}", gameId);
            return;
        }
        if (game.isFinal()) {
            rankingService.removeLive(gameId);
            return;
        }
        if (!game.isLive()) {
            return;
        }

        List<Play> recentPlays = playRepository.findByGameIdOrderByPlayOrderDesc(
                gameId, PageRequest.of(0, props.leadChange().windowPlays()));
        Collections.reverse(recentPlays); // 시간순 오름차순으로

        ScoreCalculator.Result result = calculator.calculate(game, recentPlays, Instant.now());

        // TODO(담당: standings 파트): 경기 중요도 보정 ×0.9~1.15
        double importance = 1.0;
        // TODO(담당: pregame 파트): pregame_score 상위 경기 사전 가산 (최대 +10)
        double pregameBonus = 0.0;

        double watchScore = calculator.clampWatchScore(result.baseScore() * importance + pregameBonus);

        WatchScore record = new WatchScore();
        record.setGameId(gameId);
        record.setBaseScore(result.baseScore());
        record.setWatchScore(watchScore);
        record.setSignals(result.signals());
        record.setReasonTags(ReasonTags.from(result.signals()));
        record.setConfigVersion(props.version());
        record.setCreatedAt(Instant.now());
        watchScoreRepository.save(record);

        rankingService.updateLive(gameId, watchScore);
        log.debug("scored gameId={} watchScore={} signals={}", gameId, watchScore, result.signals());
    }
}
