package com.pulse.scorer;

import com.pulse.common.config.ScoringProperties;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.ranking.RankingService;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreRecalculationService {

    private final GameRepository gameRepository;
    private final PlayRepository playRepository;
    private final WatchScoreRepository watchScoreRepository;
    private final ScoreCalculator calculator;
    private final RankingService rankingService;
    private final ScoringProperties props;

    @Transactional
    public void recalculate(long gameId, Instant observedAt) {
        if (watchScoreRepository.existsByGameIdAndComputedAt(gameId, observedAt)) {
            log.debug("replay score skipped for already computed cycle: gameId={} observedAt={}", gameId, observedAt);
            return;
        }

        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null) {
            log.debug("replay score skipped for unknown game: {}", gameId);
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
        Collections.reverse(recentPlays);

        ScoreCalculator.Result result = calculator.calculate(game, recentPlays, observedAt);
        double watchScore = calculator.clampWatchScore(result.baseScore());
        List<String> reasonTags = ReasonTags.from(result.signals());
        Play latestPlay = recentPlays.isEmpty() ? null : recentPlays.get(recentPlays.size() - 1);

        WatchScore record = new WatchScore();
        record.setGameId(gameId);
        record.setComputedAt(observedAt);
        record.setPlayOrder(latestPlay == null ? null : latestPlay.getPlayOrder());
        record.setInning(latestPlay == null ? game.getPeriod() : latestPlay.getInning());
        record.setInningType(latestPlay == null ? null : latestPlay.getInningType());
        record.setBaseScore(roundScore(result.baseScore()));
        record.setWatchScore(roundScore(watchScore));
        record.setSignalContributions(result.signals());
        record.setTags(reasonTags);
        record.setBackfilled(false);
        record.setSource("OPERATIONAL");
        watchScoreRepository.save(record);

        rankingService.updateLive(gameId, watchScore);
        log.debug("replayed score gameId={} watchScore={} observedAt={}", gameId, watchScore, observedAt);
    }

    private static int roundScore(double score) {
        return (int) Math.round(score);
    }
}
