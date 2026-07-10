package com.pulse.ai;

import java.util.Map;

public record AiEventCopyRequest(
        long gameId,
        long eventId,
        String mode,
        String contextHash,
        SafeContext safeContext,
        String language,
        int maxLength
) {

    public record SafeContext(
            String eventType,
            String label,
            Integer inning,
            String inningType,
            Map<String, Object> evidence
    ) {
    }
}