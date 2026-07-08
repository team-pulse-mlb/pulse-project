package com.pulse.common.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationEvent(
        UUID eventId,
        NotificationType type,
        long gameId,
        List<String> tags,
        Instant occurredAt
) {

    public enum NotificationType {
        SURGE,
        GAME_START
    }
}
