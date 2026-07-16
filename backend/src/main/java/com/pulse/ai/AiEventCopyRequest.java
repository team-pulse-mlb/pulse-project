package com.pulse.ai;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

public record AiEventCopyRequest(
        long gameId,
        long eventId,
        String mode,
        String contextHash,
        SafeContext safeContext
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SafeContext(
            String eventType,
            String label,
            Integer inning,
            List<String> contributingLabels,
            Map<String, Object> situation,
            String inningType,
            String batter,
            String pitcher,
            Map<String, Object> evidence
    ) {
    }
}
