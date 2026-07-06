package com.pulse.replay.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.replay.ReplayProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

@Component
@Profile("migration")
@RequiredArgsConstructor
@Slf4j
class MigrationRawArchiveClient {

    static final List<String> PREFIXES = List.of(
            "raw/games/",
            "raw/plays/",
            "raw/backfill/plays/",
            "raw/plate_appearances/",
            "raw/backfill/plate_appearances/",
            "raw/odds/",
            "raw/standings/",
            "raw/lineups/"
    );

    private final ReplayProperties properties;
    private final ObjectMapper objectMapper;

    List<MigrationEnvelope> loadPrefix(String prefix) {
        String bucket = bucket();
        try (S3Client s3 = s3Client()) {
            List<MigrationEnvelope> envelopes = new ArrayList<>();
            for (String key : listKeys(s3, bucket, prefix)) {
                envelopes.add(readEnvelope(s3, bucket, key));
            }
            envelopes.sort(Comparator
                    .comparing(MigrationEnvelope::observedAt)
                    .thenComparing(MigrationEnvelope::key));
            log.info("이전 배치 S3 객체 목록: prefix={}, objects={}", prefix, envelopes.size());
            return envelopes;
        }
    }

    private List<String> listKeys(S3Client s3, String bucket, String prefix) {
        List<String> keys = new ArrayList<>();
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();
        for (S3Object object : s3.listObjectsV2Paginator(request).contents()) {
            if (object.key().endsWith(".json.gz")) {
                keys.add(object.key());
            }
        }
        return keys;
    }

    private MigrationEnvelope readEnvelope(S3Client s3, String bucket, String key) {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3.getObject(
                    request -> request.bucket(bucket).key(key),
                    ResponseTransformer.toBytes());
            JsonNode root = objectMapper.readTree(gunzip(bytes.asByteArray()));
            return new MigrationEnvelope(
                    key,
                    parseInstant(root.path("observed_at").asText(null)),
                    root.path("endpoint").asText(""),
                    root.path("params"),
                    root.path("response"),
                    root.path("backfilled").asBoolean(false)
            );
        } catch (Exception e) {
            throw new IllegalStateException("이전 배치 S3 객체 읽기 실패: s3://" + bucket + "/" + key, e);
        }
    }

    private S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder();
        String region = properties.s3() == null ? null : properties.s3().region();
        if (region != null && !region.isBlank()) {
            builder.region(Region.of(region));
        }
        return builder.build();
    }

    private String bucket() {
        String bucket = properties.s3() == null ? null : properties.s3().bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("migration 프로필에는 pulse.replay.s3.bucket 설정이 필요합니다");
        }
        return bucket;
    }

    private static byte[] gunzip(byte[] bytes) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return gzip.readAllBytes();
        }
    }

    static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        return Instant.parse(value);
    }
}

