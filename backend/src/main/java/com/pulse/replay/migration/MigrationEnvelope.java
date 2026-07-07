package com.pulse.replay.migration;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

record MigrationEnvelope(
        String key,
        Instant observedAt,
        String endpoint,
        JsonNode params,
        JsonNode response,
        boolean backfilled
) {
}

