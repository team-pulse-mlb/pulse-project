package com.pulse.ai;

import java.util.Map;

public record AiEventCopyRequest(
        long gameId,
        long eventId,
        String mode,
        String contextHash,
        SafeContext safeContext
) {

    public record SafeContext(
            String eventType,
            String label,
            Integer inning,
            String inningType,
            String batter,
            String pitcher,
            Map<String, Object> evidence
    ) {
    }
}