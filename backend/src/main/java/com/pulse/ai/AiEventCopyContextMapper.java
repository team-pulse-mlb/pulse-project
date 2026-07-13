package com.pulse.ai;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AiEventCopyContextMapper {

    private static final String REVEALED_MODE = "REVEALED";

    public AiEventCopyRequest.SafeContext toSafeContext(
            String mode,
            String eventType,
            String label,
            Integer inning,
            String inningType,
            Map<String, Object> payload
    ) {
        if (isRevealed(mode)) {
            return new AiEventCopyRequest.SafeContext(
                    normalizeText(eventType),
                    normalizeText(label),
                    inning,
                    normalizeText(inningType),
                    copyEvidence(payload)
            );
        }

        return new AiEventCopyRequest.SafeContext(
                normalizeText(eventType),
                normalizeText(label),
                inning,
                null,
                null
        );
    }

    private boolean isRevealed(String mode) {
        return REVEALED_MODE.equalsIgnoreCase(normalizeText(mode));
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private Map<String, Object> copyEvidence(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> evidence = new LinkedHashMap<>();

        payload.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                evidence.put(key.trim(), value);
            }
        });

        return Map.copyOf(evidence);
    }
}