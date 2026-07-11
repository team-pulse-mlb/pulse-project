package com.pulse.common.ai;

import java.util.Map;

public record RevealedEventCopyContext(
        long gameId,
        long eventId,
        String eventType,
        String label,
        Integer inning,
        String contextHash,
        String inningType,
        String batter,
        String pitcher,
        Map<String, Object> evidence
) implements EventCopyContext {
}
