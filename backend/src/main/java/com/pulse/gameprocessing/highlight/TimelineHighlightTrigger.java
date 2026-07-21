package com.pulse.gameprocessing.highlight;

import com.pulse.gameprocessing.aicopy.AiGenerationTrigger;
import com.pulse.gameprocessing.effect.SurgeDetector;
import com.pulse.common.config.ScoringProperties;
import com.pulse.common.metrics.PulseMetrics;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventLabelPolicy;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.WatchScoreRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 보호 모드 이벤트 타임라인 하이라이트 트리거.
 *
 * 이벤트 태그마다 AI 문구를 만들면 같은 이닝의 유사 이벤트가 저정보·중복 문구로 쌓인다.
 * 대신 추천 점수가 급변한 순간에만, 그 구간의 anchor 보호 이벤트를 하이라이트로 표시하고
 * 보호 모드 문구 생성을 1회 요청한다.
 *
 * 급변을 스포일러 없이 설명할 보호 이벤트가 윈도에 하나도 없으면(결과성 사건만으로 급변한 경우)
 * 하이라이트를 만들지 않는다. 그 순간은 보호 모드에서 노출하지 않는 것이 안전하다.
 *
 * 알림용 {@link SurgeDetector}와 임계·쿨다운을 분리해, 타임라인은 더 촘촘히 하이라이트를 남긴다.
 * {@code scoring.highlight.enabled=false}면 아무 것도 하지 않아, 기존 이벤트별 트리거를 그대로 둔다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TimelineHighlightTrigger {

    private final GameEventRepository gameEventRepository;
    private final WatchScoreRepository watchScoreRepository;
    private final AiGenerationTrigger aiGenerationTrigger;
    private final AfterCommitExecutor afterCommitExecutor;
    private final ScoringProperties props;

    /**
     * 이번 사이클이 급변 하이라이트인지 판정하고, 맞으면 anchor 이벤트를 표시·문구 생성 요청한다.
     * watch_score는 이번 사이클 값이 이미 영속된 뒤에 호출한다(윈도 최저값 비교에 현재 값 포함).
     */
    public void evaluate(long gameId, int watchScore, Instant now) {
        ScoringProperties.Highlight config = props.highlight();
        if (config == null || !config.enabled()) {
            return;
        }
        if (watchScore < config.minScore()) {
            return;
        }

        Instant cooldownSince = now.minus(Duration.ofMinutes(config.cooldownMinutes()));
        if (gameEventRepository.existsByGameIdAndTimelineHighlightTrueAndObservedAtGreaterThanEqual(
                gameId, cooldownSince)) {
            return;
        }

        Instant riseSince = now.minus(Duration.ofMinutes(config.riseWindowMinutes()));
        Integer minInWindow = watchScoreRepository.findMinWatchScoreSince(gameId, riseSince);
        if (minInWindow == null || watchScore - minInWindow < config.riseScore()) {
            return;
        }

        List<GameEvent> anchorCandidates = gameEventRepository
                .findByGameIdAndSpoilerLevelAndTimelineHighlightFalseAndObservedAtGreaterThanEqualOrderByObservedAtAscIdAsc(
                        gameId, GameEvent.SPOILER_PROTECTED_SAFE, riseSince)
                .stream()
                .filter(event -> event.getObservedAt() != null)
                .filter(event -> GameEventLabelPolicy.protectedLabel(
                        event.getSpoilerLevel(), event.getEventType()) != null)
                .toList();
        Set<String> avoidTypes = gameEventRepository
                .findFirstByGameIdAndTimelineHighlightTrueOrderByObservedAtDescIdDesc(gameId)
                .map(GameEvent::getEventType)
                .map(Set::of)
                .orElseGet(Set::of);
        GameEvent anchor = TimelineHighlightAnchorSelector.selectAnchor(anchorCandidates, avoidTypes);
        if (anchor == null) {
            log.debug("하이라이트 anchor 없음(보호 이벤트 부재로 스킵) gameId={} watchScore={}", gameId, watchScore);
            return;
        }

        anchor.setTimelineHighlight(true);
        gameEventRepository.save(anchor);
        PulseMetrics.increment("pulse.scorer.highlight.fired");

        long anchorId = anchor.getId();
        afterCommitExecutor.execute(() -> aiGenerationTrigger.onGameEventPersisted(
                gameId, anchorId, AiGenerationTrigger.MODE_PROTECTED, now));
        log.debug("타임라인 하이라이트 표시 gameId={} eventId={} watchScore={}", gameId, anchorId, watchScore);
    }
}
