package com.pulse.ai;

import java.util.List;

public record AiSafeContext(
        String gameStatus,
        String inningPhase,
        List<String> safeTags,
        List<String> reasonCodes
) {

    public AiSafeContext {
        // ai-service 요청을 단순하게 만들기 위해 list 필드는 항상 non-null로 유지한다.
        safeTags = copyOrEmpty(safeTags);
        reasonCodes = copyOrEmpty(reasonCodes);
    }

    private static List<String> copyOrEmpty(List<String> values) {
        if (values == null) {
            return List.of();
        }

        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}