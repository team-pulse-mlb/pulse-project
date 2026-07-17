package com.pulse.api.home;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulse.home.ranking-cache")
public record HomeRankingCacheProperties(Duration ttl) {

    public HomeRankingCacheProperties {
        if (ttl == null) {
            ttl = Duration.ofSeconds(3);
        }
    }
}
