package com.pulse.scorer;

import com.pulse.domain.GameEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 급변 윈도 안의 보호 이벤트 후보에서 하이라이트 anchor를 고른다.
 *
 * "가장 최근" 대신 정보량이 큰 유형을 우선한다. hard_contact는 발생 빈도가 가장 높아
 * 최근 우선 선택에서 anchor를 과대 점유하고, 같은 라벨 문구가 연속 노출되는 원인이 됐다.
 * 직전 하이라이트와 같은 유형은 회피하되, 윈도에 그 유형뿐이면 그대로 허용해
 * 하이라이트 밀도를 유지한다.
 */
final class TimelineHighlightAnchorSelector {

    private static final Map<String, Integer> EVENT_TYPE_RANKS = Map.of(
            "pressure_bases_loaded", 0,
            "pressure_scoring_position", 1,
            "full_count_two_out", 2,
            "pitcher_instability", 3,
            "long_at_bat", 4,
            "hard_contact", 5
    );
    private static final int UNKNOWN_EVENT_TYPE_RANK = Integer.MAX_VALUE;
    private static final Comparator<GameEvent> ANCHOR_ORDER =
            Comparator.comparingInt(TimelineHighlightAnchorSelector::rank)
                    .thenComparing(GameEvent::getObservedAt, Comparator.reverseOrder())
                    .thenComparing(GameEvent::getId, Comparator.reverseOrder());

    private TimelineHighlightAnchorSelector() {
    }

    static GameEvent selectAnchor(List<GameEvent> candidates, Set<String> avoidTypes) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        Set<String> typesToAvoid = avoidTypes == null ? Set.of() : avoidTypes;
        List<GameEvent> preferredCandidates = candidates.stream()
                .filter(candidate -> !typesToAvoid.contains(candidate.getEventType()))
                .toList();
        List<GameEvent> selectionPool = preferredCandidates.isEmpty() ? candidates : preferredCandidates;

        return selectionPool.stream()
                .min(ANCHOR_ORDER)
                .orElse(null);
    }

    private static int rank(GameEvent event) {
        return EVENT_TYPE_RANKS.getOrDefault(event.getEventType(), UNKNOWN_EVENT_TYPE_RANK);
    }
}
