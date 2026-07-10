package com.pulse.scorer;

import com.pulse.common.config.ScoringProperties;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.Play;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 흥미 순간 이벤트(game_events) 추출. 라이브 계산 중 임계 통과분을 append하며,
 * (game_id, event_type, source_type, source_ref) UNIQUE로 재관측 시 중복을 막는다.
 * 종료 후 재계산하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class GameEventExtractor {

    static final String EVENT_SCORING_PLAY = "SCORING_PLAY";
    static final String EVENT_LEAD_CHANGE = "LEAD_CHANGE";

    private final GameEventRepository gameEventRepository;
    private final AiGenerationTrigger aiGenerationTrigger;
    private final ScoringProperties props;

    /** 최근 play 창에서 득점·리드 변경 이벤트를 추출해 dedupe append한다. */
    public void extract(long gameId, List<Play> recentPlays, Instant observedAt) {
        int lastLeader = 0;
        for (Play play : recentPlays) {
            if (Boolean.TRUE.equals(play.getScoringPlay())) {
                appendIfAbsent(gameId, EVENT_SCORING_PLAY, play, observedAt, scorePayload(play));
            }
            Integer leader = leaderOf(play);
            if (leader != null && leader != 0) {
                if (lastLeader != 0 && leader != lastLeader) {
                    appendIfAbsent(gameId, EVENT_LEAD_CHANGE, play, observedAt, Map.of());
                }
                lastLeader = leader;
            }
        }
    }

    private void appendIfAbsent(long gameId, String eventType, Play play, Instant observedAt, Map<String, Object> payload) {
        if (play.getPlayOrder() == null) {
            return;
        }
        boolean exists = gameEventRepository.existsByGameIdAndEventTypeAndSourceTypeAndSourceRef(
                gameId, eventType, GameEvent.SOURCE_TYPE_PLAY, play.getPlayOrder());
        if (exists) {
            return;
        }
        GameEvent event = new GameEvent();
        event.setGameId(gameId);
        event.setEventType(eventType);
        event.setSpoilerLevel(GameEvent.SPOILER_REVEALED_ONLY);
        event.setSourceType(GameEvent.SOURCE_TYPE_PLAY);
        event.setSourceRef(play.getPlayOrder());
        event.setInning(play.getInning());
        event.setInningType(play.getInningType());
        event.setBatterId(play.getBatterId());
        event.setPitcherId(play.getPitcherId());
        event.setPayload(payload.isEmpty() ? null : payload);
        event.setRulesetVersion(String.valueOf(props.version()));
        event.setObservedAt(observedAt);
        event.setBackfilled(false);
        event.setSource("OPERATIONAL");
        GameEvent saved = gameEventRepository.save(event);
        requestEventCopy(saved, observedAt);
    }

    private void requestEventCopy(GameEvent event, Instant observedAt) {
        if (GameEvent.SPOILER_PROTECTED_SAFE.equals(event.getSpoilerLevel())) {
            aiGenerationTrigger.onGameEventPersisted(
                    event.getGameId(), event.getId(), AiGenerationTrigger.MODE_PROTECTED, observedAt);
        }
        aiGenerationTrigger.onGameEventPersisted(
                event.getGameId(), event.getId(), AiGenerationTrigger.MODE_REVEALED, observedAt);
    }

    private static Map<String, Object> scorePayload(Play play) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (play.getScoreValue() != null) {
            payload.put("scoreValue", play.getScoreValue());
        }
        return payload;
    }

    private static Integer leaderOf(Play play) {
        if (play.getHomeScore() == null || play.getAwayScore() == null) {
            return null;
        }
        return Integer.signum(play.getHomeScore() - play.getAwayScore());
    }
}
