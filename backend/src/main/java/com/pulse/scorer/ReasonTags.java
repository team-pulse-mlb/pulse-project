package com.pulse.scorer;

import java.util.List;
import java.util.Map;

/**
 * 내부 신호를 보호 모드에서도 안전한 추천 태그로 바꾼다.
 */
public final class ReasonTags {

    private static final Map<String, String> TAG_BY_SIGNAL = Map.of(
            "late_or_extra", "후반 긴장 구간",
            "score_gap", "접전 흐름",
            "recent_score", "흐름 변화",
            "lead_change", "흐름 급변",
            "big_inning", "한 이닝 흐름 집중",
            "pressure", "득점권 압박",
            "early_slugfest", "초반 난타 흐름"
    );

    private ReasonTags() {
    }

    public static List<String> from(Map<String, Double> signals) {
        return from(signals, false);
    }

    public static List<String> from(Map<String, Double> signals, boolean fullCountIncluded) {
        List<String> tags = new java.util.ArrayList<>(signals.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .map(e -> TAG_BY_SIGNAL.get(e.getKey()))
                .filter(tag -> tag != null)
                .toList());
        if (fullCountIncluded) {
            tags.add("풀카운트 승부");
        }
        return List.copyOf(tags);
    }
}
