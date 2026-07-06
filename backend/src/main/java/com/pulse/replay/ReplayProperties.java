package com.pulse.replay;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulse.replay")
public record ReplayProperties(
        S3 s3,
        Long gameId,
        String date,
        int maxObjectsPerPrefix
) {
    public ReplayProperties {
        if (maxObjectsPerPrefix <= 0) {
            maxObjectsPerPrefix = 200;
        }
    }

    public record S3(String bucket, String region) {
    }
}
