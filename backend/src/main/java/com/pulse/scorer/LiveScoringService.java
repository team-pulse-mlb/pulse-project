package com.pulse.scorer;

import com.pulse.scoring.ImportanceCalculator;
import com.pulse.scoring.ReasonTags;
import com.pulse.scoring.ScoreCalculator;
import com.pulse.scoring.ScoringInput;
import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 라이브 ScoreTask 처리. base_score 계산, watch_scores 적재, peak/게임 이벤트 갱신,
 * Redis 랭킹·캐시·신호 발행, SURGE 판정을 수행한다. (gameId, computedAt) UNIQUE로 멱등하다.
 */
@Service
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class LiveScoringService {

    private final GameRepository gameRepository;
    private final PlayRepository playRepository;
    private final WatchScoreRepository watchScoreRepository;
    private final ScoreCalculator calculator;
    private final ImportanceCalculator importanceCalculator;
    private final GameEventExtractor gameEventExtractor;
    private final LiveSignalPublisher liveSignalPublisher;
    private final TimelineHighlightTrigger timelineHighlightTrigger;
    private final ScoringProperties props;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void handle(ScoreTask task) {
        Instant observedAt = task.observedAt();
        long gameId = task.gameId();

        if (watchScoreRepository.existsByGameIdAndComputedAt(gameId, observedAt)) {
            log.debug("이미 계산된 사이클 skip: gameId={} computedAt={}", gameId, observedAt);
            return;
        }

        Game game = gameRepository.findById(gameId).orElse(null);
        if (game == null) {
            log.debug("알 수 없는 경기 skip: {}", gameId);
            return;
        }

        if (!game.isLive()) {
            liveSignalPublisher.removeLiveGame(gameId);
            liveSignalPublisher.evictGameCache(gameId);
            liveSignalPublisher.publishGameSignal(gameId);
            liveSignalPublisher.publishRankingSignal();
            log.debug("지연된 라이브 task의 종료 경기 정리: gameId={} status={}", gameId, game.getStatus());
            return;
        }

        List<Play> recentPlays = task.lastPlayOrder() == null
                ? new ArrayList<>()
                : playRepository.findByGameIdAndPlayOrderLessThanEqualOrderByPlayOrderDesc(
                        gameId,
                        task.lastPlayOrder(),
                        PageRequest.of(0, props.leadChange().windowPlays() + 1));
        Collections.reverse(recentPlays);
        int seedLeader = 0;
        if (recentPlays.size() > props.leadChange().windowPlays()) {
            seedLeader = leaderOf(recentPlays.get(0));
            recentPlays = recentPlays.subList(1, recentPlays.size());
        }

        ScoreTask.GameSnapshot gameSnapshot = task.gameSnapshot();
        double importance = gameSnapshot == null
                ? importanceCalculator.multiplier(game)
                : importanceCalculator.multiplier(game, gameSnapshot.postseason());
        ScoreCalculator.Result result = calculator.calculate(new ScoringInput(
                game,
                recentPlays,
                task.situation(),
                seedLeader,
                observedAt,
                importance,
                game.getPregameScore() == null ? 0 : game.getPregameScore(),
                gameSnapshot));
        double baseScore = result.baseScore();
        double watchScore = result.watchScore();
        int watchScoreRounded = (int) Math.round(watchScore);

        List<String> tags = ReasonTags.from(result.signals(), result.fullCountIncluded());
        Play latestPlay = recentPlays.isEmpty() ? null : recentPlays.get(recentPlays.size() - 1);
        List<String> previousTags = watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(gameId)
                .map(WatchScore::getTags)
                .orElse(List.of());

        Integer fallbackInning = gameSnapshot == null ? game.getPeriod() : gameSnapshot.period();
        persistWatchScore(game, observedAt, latestPlay, fallbackInning, result, watchScoreRounded, tags);
        updatePeakBaseScore(game, baseScore);
        gameEventExtractor.extract(gameId, recentPlays, task.plateAppearances(), seedLeader, observedAt);

        Integer eventInning = latestPlay == null ? fallbackInning : latestPlay.getInning();
        String eventInningType = latestPlay == null ? null : latestPlay.getInningType();
        Long scoredPlayOrder = latestPlay == null ? game.getLastPlayOrder() : latestPlay.getPlayOrder();

        // 급변 순간의 anchor 보호 이벤트를 하이라이트로 표시하고 보호 문구 생성을 요청한다.
        // (scoring.highlight.enabled=false면 no-op)
        timelineHighlightTrigger.evaluate(gameId, watchScoreRounded, observedAt);

        // Redis projection, SURGE 판정·알림, 미번역 플레이 생성 요청은 커밋 후
        // LiveScoreComputedEvent 리스너가 각각 처리한다.
        applicationEventPublisher.publishEvent(new LiveScoreComputedEvent(
                gameId, observedAt, watchScore, watchScoreRounded, (int) Math.round(baseScore),
                tags, previousTags, eventInning, eventInningType, scoredPlayOrder,
                task.lastPlayOrder(), game.getLifecycleState(), props.version()));

        log.debug("라이브 점수 계산 gameId={} watchScore={} observedAt={}", gameId, watchScoreRounded, observedAt);
    }

    private void persistWatchScore(
            Game game,
            Instant observedAt,
            Play latestPlay,
            Integer fallbackInning,
            ScoreCalculator.Result result,
            int watchScoreRounded,
            List<String> tags
    ) {
        WatchScore record = new WatchScore();
        record.setGameId(game.getId());
        record.setComputedAt(observedAt);
        record.setPlayOrder(latestPlay == null ? game.getLastPlayOrder() : latestPlay.getPlayOrder());
        record.setInning(latestPlay == null ? fallbackInning : latestPlay.getInning());
        record.setInningType(latestPlay == null ? null : latestPlay.getInningType());
        record.setBaseScore((int) Math.round(result.baseScore()));
        record.setImportanceMultiplier(BigDecimal.valueOf(result.importanceMultiplier())
                .setScale(2, RoundingMode.HALF_UP));
        record.setPregameBonus(BigDecimal.valueOf(result.pregameBonus())
                .setScale(2, RoundingMode.HALF_UP));
        record.setWatchScore(watchScoreRounded);
        record.setScoringVersion(props.version());
        record.setSignalContributions(result.signals());
        record.setTags(tags);
        record.setBackfilled(false);
        record.setSource("OPERATIONAL");
        watchScoreRepository.save(record);
    }

    private void updatePeakBaseScore(Game game, double baseScore) {
        int rounded = (int) Math.round(baseScore);
        int currentPeak = game.getPeakBaseScore() == null ? 0 : game.getPeakBaseScore();
        if (rounded > currentPeak) {
            game.setPeakBaseScore(rounded);
            gameRepository.save(game);
        }
    }

    private static int leaderOf(Play play) {
        if (play.getHomeScore() == null || play.getAwayScore() == null) {
            return 0;
        }
        return Integer.signum(play.getHomeScore() - play.getAwayScore());
    }
}
