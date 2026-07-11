package com.pulse.replay.rescore;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulse.rescore")
public record RescoreProperties(List<Long> gameIds) {

    public RescoreProperties {
        gameIds = gameIds == null ? List.of() : List.copyOf(gameIds);
    }
}
