package com.pulse.gameprocessing.highlight;

import com.pulse.gameprocessing.aicopy.AiGenerationTrigger;
import com.pulse.common.config.ScoringProperties;
import com.pulse.common.metrics.PulseMetrics;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventLabelPolicy;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 타임라인 하이라이트가 하나도 없는 종료 경기를 점수 이력으로 채운다.
 *
 * 라이브 트리거와 달리 종료 백필에서는 급변 순간의 절대 점수가 {@code minScore} 이상인지
 * 검사하지 않는다. 급변 폭, 쿨다운, 보호 라벨이 있는 anchor 이벤트 조건은 유지하며,
 * 결과성 사건만 있는 급변 구간은 보호 모드에 노출하지 않는다.
 *
 * 경기별 하이라이트가 한 건이라도 있으면 전체 작업을 건너뛰므로 종료 task 재전달과
 * 재처리 배치 반복 실행에도 멱등이다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.game-processor", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TimelineHighlightBackfill {

    private final GameEventRepository gameEventRepository;
    private final WatchScoreRepository watchScoreRepository;
    private final AiGenerationTrigger aiGenerationTrigger;
    private final AfterCommitExecutor afterCommitExecutor;
    private final ScoringProperties props;

    /**
     * 하이라이트가 없는 경기에 급변 구간의 보호 anchor를 표시한다.
     * 라이브 트리거의 절대 점수 하한만 완화하고 나머지 안전 조건과 멱등 기준은 유지한다.
     *
     * @return 새로 표시한 하이라이트 수
     */
    @Transactional
    public int backfillIfEmpty(long gameId, Instant now, boolean requestCopyGeneration) {
        ScoringProperties.Highlight config = props.highlight();
        if (config == null || !config.enabled()) {
            return 0;
        }
        if (gameEventRepository.existsByGameIdAndTimelineHighlightTrue(gameId)) {
            return 0;
        }

        List<WatchScore> scores = watchScoreRepository.findByGameIdOrderByComputedAtAsc(gameId);
        if (scores.isEmpty()) {
            return 0;
        }

        List<RiseCandidate> riseCandidates = findRiseCandidates(scores, config);
        if (riseCandidates.isEmpty()) {
            return 0;
        }

        List<GameEvent> anchorCandidates = gameEventRepository
                .findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                        gameId, GameEvent.SPOILER_PROTECTED_SAFE)
                .stream()
                .filter(event -> !event.isTimelineHighlight())
                .filter(event -> event.getObservedAt() != null)
                .filter(event -> GameEventLabelPolicy.protectedLabel(
                        event.getSpoilerLevel(), event.getEventType()) != null)
                .toList();

        List<GameEvent> selectedAnchors = selectAnchors(
                riseCandidates, anchorCandidates, config, config.backfillMaxPerGame());

        markHighlights(gameId, now, requestCopyGeneration, selectedAnchors);

        if (!selectedAnchors.isEmpty()) {
            log.debug("종료 경기 타임라인 하이라이트 백필 gameId={} count={}",
                    gameId, selectedAnchors.size());
        }
        return selectedAnchors.size();
    }

    /** 기존 하이라이트를 모두 해제한 뒤 경기당 상한 없이 다시 생성한다. */
    @Transactional
    public int rebuildHighlights(long gameId, Instant now, boolean requestCopyGeneration) {
        ScoringProperties.Highlight config = props.highlight();
        if (config == null || !config.enabled()) {
            return 0;
        }

        List<GameEvent> existingHighlights = gameEventRepository
                .findByGameIdAndSpoilerLevelAndTimelineHighlightTrueOrderByObservedAtAscIdAsc(
                        gameId, GameEvent.SPOILER_PROTECTED_SAFE);
        for (GameEvent existingHighlight : existingHighlights) {
            existingHighlight.setTimelineHighlight(false);
            gameEventRepository.save(existingHighlight);
        }

        List<WatchScore> scores = watchScoreRepository.findByGameIdOrderByComputedAtAsc(gameId);
        if (scores.isEmpty()) {
            return 0;
        }

        List<RiseCandidate> riseCandidates = findRiseCandidates(scores, config);
        if (riseCandidates.isEmpty()) {
            return 0;
        }

        List<GameEvent> anchorCandidates = gameEventRepository
                .findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                        gameId, GameEvent.SPOILER_PROTECTED_SAFE)
                .stream()
                .filter(event -> !event.isTimelineHighlight())
                .filter(event -> event.getObservedAt() != null)
                .filter(event -> GameEventLabelPolicy.protectedLabel(
                        event.getSpoilerLevel(), event.getEventType()) != null)
                .toList();

        List<GameEvent> selectedAnchors = selectAnchors(
                riseCandidates, anchorCandidates, config, null);
        markHighlights(gameId, now, requestCopyGeneration, selectedAnchors);

        if (!selectedAnchors.isEmpty()) {
            log.debug("종료 경기 타임라인 하이라이트 삭제 후 재생성 gameId={} count={}",
                    gameId, selectedAnchors.size());
        }
        return selectedAnchors.size();
    }

    private static List<GameEvent> selectAnchors(
            List<RiseCandidate> riseCandidates,
            List<GameEvent> anchorCandidates,
            ScoringProperties.Highlight config,
            Integer maxPerGame
    ) {
        List<Instant> selectedTimes = new ArrayList<>();
        Set<Long> selectedAnchorIds = new HashSet<>();
        List<GameEvent> selectedAnchors = new ArrayList<>();
        Duration cooldown = Duration.ofMinutes(config.cooldownMinutes());
        Duration riseWindow = Duration.ofMinutes(config.riseWindowMinutes());

        for (RiseCandidate riseCandidate : riseCandidates) {
            if (maxPerGame != null && selectedAnchors.size() >= maxPerGame) {
                break;
            }
            boolean inCooldown = selectedTimes.stream()
                    .anyMatch(selectedTime -> Duration.between(selectedTime, riseCandidate.time())
                            .abs()
                            .compareTo(cooldown) < 0);
            if (inCooldown) {
                continue;
            }

            Instant windowStart = riseCandidate.time().minus(riseWindow);
            List<GameEvent> candidatesInWindow = anchorCandidates.stream()
                    .filter(event -> !selectedAnchorIds.contains(event.getId()))
                    .filter(event -> !event.getObservedAt().isBefore(windowStart))
                    .filter(event -> !event.getObservedAt().isAfter(riseCandidate.time()))
                    .toList();
            Set<String> avoidTypes = selectedAnchors.stream()
                    .filter(event -> Duration.between(event.getObservedAt(), riseCandidate.time())
                            .abs()
                            .compareTo(riseWindow) <= 0)
                    .map(GameEvent::getEventType)
                    .collect(Collectors.toSet());
            GameEvent anchor = TimelineHighlightAnchorSelector.selectAnchor(candidatesInWindow, avoidTypes);
            if (anchor == null) {
                continue;
            }

            selectedTimes.add(riseCandidate.time());
            selectedAnchorIds.add(anchor.getId());
            selectedAnchors.add(anchor);
        }

        return selectedAnchors;
    }

    private void markHighlights(
            long gameId,
            Instant now,
            boolean requestCopyGeneration,
            List<GameEvent> selectedAnchors
    ) {
        for (GameEvent anchor : selectedAnchors) {
            anchor.setTimelineHighlight(true);
            gameEventRepository.save(anchor);
            PulseMetrics.increment("pulse.game-processor.highlight.backfilled");

            if (requestCopyGeneration) {
                long anchorId = anchor.getId();
                afterCommitExecutor.execute(() -> aiGenerationTrigger.onGameEventPersisted(
                        gameId, anchorId, AiGenerationTrigger.MODE_PROTECTED, now));
            }
        }
    }

    private static List<RiseCandidate> findRiseCandidates(
            List<WatchScore> scores,
            ScoringProperties.Highlight config
    ) {
        List<RiseCandidate> candidates = new ArrayList<>();
        Duration riseWindow = Duration.ofMinutes(config.riseWindowMinutes());

        for (WatchScore reached : scores) {
            if (reached.getComputedAt() == null || reached.getWatchScore() == null) {
                continue;
            }
            Instant windowStart = reached.getComputedAt().minus(riseWindow);
            Integer minScore = scores.stream()
                    .filter(score -> score.getComputedAt() != null && score.getWatchScore() != null)
                    .filter(score -> !score.getComputedAt().isBefore(windowStart))
                    .filter(score -> !score.getComputedAt().isAfter(reached.getComputedAt()))
                    .map(WatchScore::getWatchScore)
                    .min(Integer::compareTo)
                    .orElse(null);
            if (minScore == null) {
                continue;
            }
            int rise = reached.getWatchScore() - minScore;
            if (rise >= config.riseScore()) {
                candidates.add(new RiseCandidate(reached.getComputedAt(), rise, reached.getWatchScore()));
            }
        }

        candidates.sort(Comparator.comparingInt(RiseCandidate::rise).reversed()
                .thenComparing(Comparator.comparingInt(RiseCandidate::reachedScore).reversed())
                .thenComparing(RiseCandidate::time));
        return candidates;
    }

    private record RiseCandidate(Instant time, int rise, int reachedScore) {
    }
}
