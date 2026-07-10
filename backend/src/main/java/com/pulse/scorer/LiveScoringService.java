package com.pulse.scorer;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.NotificationEvent;
import com.pulse.common.message.NotificationEvent.NotificationType;
import com.pulse.common.message.NotificationEventPublisher;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.NotificationEventLog;
import com.pulse.domain.NotificationEventLogRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 라이브 ScoreTask 처리. base_score 계산, watch_scores 적재, peak/게임 이벤트 갱신,
 * Redis 랭킹·캐시·신호 발행, SURGE 판정을 수행한다. (gameId, computedAt) UNIQUE로 멱등하다.
 */
@Service
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
    private final SurgeDetector surgeDetector;
    private final NotificationEventPublisher notificationEventPublisher;
    private final NotificationEventLogRepository notificationEventLogRepository;
    private final ScoringProperties props;

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

        List<Play> recentPlays = playRepository.findByGameIdOrderByPlayOrderDesc(
                gameId, PageRequest.of(0, props.leadChange().windowPlays()));
        Collections.reverse(recentPlays);

        ScoreCalculator.Result result = calculator.calculate(game, recentPlays, task.situation(), observedAt);
        double baseScore = result.baseScore();
        double importance = importanceCalculator.multiplier(game);
        double pregameBonus = pregameBonus(game);
        double watchScore = calculator.clampWatchScore(baseScore * importance + pregameBonus);
        int watchScoreRounded = (int) Math.round(watchScore);

        List<String> tags = ReasonTags.from(result.signals());
        Play latestPlay = recentPlays.isEmpty() ? null : recentPlays.get(recentPlays.size() - 1);

        persistWatchScore(game, observedAt, latestPlay, result, importance, pregameBonus, watchScoreRounded, tags);
        updatePeakBaseScore(game, baseScore);
        gameEventExtractor.extract(gameId, recentPlays, task.plateAppearances(), observedAt);

        liveSignalPublisher.publishLiveUpdate(
                gameId,
                watchScore,
                (int) Math.round(baseScore),
                tags,
                latestPlay == null ? game.getPeriod() : latestPlay.getInning(),
                latestPlay == null ? null : latestPlay.getInningType(),
                latestPlay == null ? game.getLastPlayOrder() : latestPlay.getPlayOrder(),
                game.getLifecycleState(),
                observedAt
        );

        if (surgeDetector.evaluate(gameId, watchScoreRounded, observedAt)) {
            publishSurge(game, tags, observedAt);
        }
        log.debug("라이브 점수 계산 gameId={} watchScore={} observedAt={}", gameId, watchScoreRounded, observedAt);
    }

    private void persistWatchScore(
            Game game,
            Instant observedAt,
            Play latestPlay,
            ScoreCalculator.Result result,
            double importance,
            double pregameBonus,
            int watchScoreRounded,
            List<String> tags
    ) {
        WatchScore record = new WatchScore();
        record.setGameId(game.getId());
        record.setComputedAt(observedAt);
        record.setPlayOrder(latestPlay == null ? game.getLastPlayOrder() : latestPlay.getPlayOrder());
        record.setInning(latestPlay == null ? game.getPeriod() : latestPlay.getInning());
        record.setInningType(latestPlay == null ? null : latestPlay.getInningType());
        record.setBaseScore((int) Math.round(result.baseScore()));
        record.setImportanceMultiplier(BigDecimal.valueOf(importance).setScale(2, RoundingMode.HALF_UP));
        record.setPregameBonus(BigDecimal.valueOf(pregameBonus).setScale(2, RoundingMode.HALF_UP));
        record.setWatchScore(watchScoreRounded);
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

    private double pregameBonus(Game game) {
        if (game.getPregameScore() == null) {
            return 0;
        }
        return Math.min(game.getPregameScore() / 10.0, props.pregameCarryoverMax());
    }

    private void publishSurge(Game game, List<String> tags, Instant occurredAt) {
        long gameId = game.getId();
        String latestTag = liveSignalPublisher.resolveLatestTag(gameId, tags, occurredAt);
        if (latestTag == null) {
            latestTag = "경기 흐름 변화";
        }
        UUID eventId = UUID.randomUUID();
        NotificationEventLog logRecord = new NotificationEventLog();
        logRecord.setEventId(eventId);
        logRecord.setType(NotificationType.SURGE.name());
        logRecord.setGameId(gameId);
        logRecord.setTags(tags);
        logRecord.setOccurredAt(occurredAt);
        notificationEventLogRepository.save(logRecord);

        notificationEventPublisher.publish(new NotificationEvent(
                eventId,
                NotificationType.SURGE,
                gameId,
                "지금 볼 만한 경기가 있어요 — " + latestTag,
                latestTag,
                occurredAt
        ));
        log.info("SURGE 알림 발행 gameId={} latestTag={}", gameId, latestTag);
    }
}
