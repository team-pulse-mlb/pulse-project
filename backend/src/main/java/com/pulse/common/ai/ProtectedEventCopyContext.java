package com.pulse.common.ai;

public record ProtectedEventCopyContext(
        long gameId,
        long eventId,
        String eventType,
        String label,
        Integer inning,
        String contextHash
) implements EventCopyContext {
}
