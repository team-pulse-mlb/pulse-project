package com.pulse.replay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
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
@Profile("replay")
@RequiredArgsConstructor
@Slf4j
public class S3RawArchiveClient {

    private final ReplayProperties properties;
    private final ObjectMapper objectMapper;

    public int streamReplayObjects(Consumer<RawEnvelope> consumer) {
        validateDate();
        String bucket = bucket();
        try (S3Client s3 = s3Client()) {
            List<String> keys = new ArrayList<>();
            keys.addAll(listKeys(s3, bucket, gamesPrefix(), properties.maxObjectsPerPrefix()));
            keys.addAll(listKeys(s3, bucket, playsPrefix(), properties.maxObjectsPerPrefix()));
            keys.addAll(listKeys(s3, bucket, backfillPlaysPrefix(), properties.maxObjectsPerPrefix()));

            List<ReplayObjectMetadata> replayObjects = new ArrayList<>();
            for (String key : keys) {
                RawEnvelope envelope = readEnvelope(s3, bucket, key);
                if (envelope != null && shouldReplay(envelope)) {
                    replayObjects.add(new ReplayObjectMetadata(
                            envelope.key(),
                            envelope.observedAt(),
                            envelope.endpoint(),
                            envelope.backfilled()));
                }
            }

            replayObjects.sort(Comparator
                    .comparing(ReplayObjectMetadata::observedAt)
                    .thenComparing(ReplayObjectMetadata::key));

            for (ReplayObjectMetadata replayObject : replayObjects) {
                consumer.accept(readEnvelope(s3, bucket, replayObject.key()));
            }
            return replayObjects.size();
        }
    }

    void validateDate() {
        if (properties.date() == null || properties.date().isBlank()) {
            throw new IllegalStateException("pulse.replay.date is required for the replay profile");
        }
    }

    private List<String> listKeys(S3Client s3, String bucket, String prefix, int maxObjects) {
        List<String> keys = new ArrayList<>();
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();
        for (S3Object object : s3.listObjectsV2Paginator(request).contents()) {
            String key = object.key();
            if (key.endsWith(".json.gz")) {
                keys.add(key);
            }
            if (keys.size() >= maxObjects) {
                break;
            }
        }
        log.info("replay archive listed {} object(s): s3://{}/{}", keys.size(), bucket, prefix);
        return keys;
    }

    private RawEnvelope readEnvelope(S3Client s3, String bucket, String key) {
        try {
            ResponseBytes<GetObjectResponse> bytes = s3.getObject(
                    request -> request.bucket(bucket).key(key),
                    ResponseTransformer.toBytes());
            JsonNode root = objectMapper.readTree(gunzip(bytes.asByteArray()));
            Instant observedAt = parseObservedAt(root.path("observed_at").asText(null));
            return new RawEnvelope(
                    key,
                    observedAt,
                    root.path("endpoint").asText(""),
                    root.path("params"),
                    root.path("response"),
                    root.path("backfilled").asBoolean(false)
            );
        } catch (Exception e) {
            throw new IllegalStateException("failed to read replay object: s3://" + bucket + "/" + key, e);
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
            throw new IllegalStateException("pulse.replay.s3.bucket is required for the replay profile");
        }
        return bucket;
    }

    private String gamesPrefix() {
        return "raw/games/dt=" + properties.date().trim() + "/";
    }

    private String playsPrefix() {
        if (properties.gameId() == null) {
            return "raw/plays/";
        }
        return "raw/plays/game_id=" + properties.gameId() + "/";
    }

    private String backfillPlaysPrefix() {
        if (properties.gameId() == null) {
            return "raw/backfill/plays/";
        }
        return "raw/backfill/plays/game_id=" + properties.gameId() + "/";
    }

    private static boolean shouldReplay(RawEnvelope envelope) {
        return "/plays".equals(envelope.endpoint()) || !envelope.backfilled();
    }

    private static byte[] gunzip(byte[] bytes) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return gzip.readAllBytes();
        }
    }

    private static Instant parseObservedAt(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        return Instant.parse(value);
    }

    public record RawEnvelope(
            String key,
            Instant observedAt,
            String endpoint,
            JsonNode params,
            JsonNode response,
            boolean backfilled
    ) {
    }

    private record ReplayObjectMetadata(
            String key,
            Instant observedAt,
            String endpoint,
            boolean backfilled
    ) {
    }
}
