package com.pulse.poller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * /plate_appearances 원본 응답을 기존 raw-archive 레이아웃으로 S3에 유지 업로드한다.
 * PA는 운영 DB에 영속하지 않는 유일한 원본이므로 이 아카이브가 소급 재추출 수단이다.
 * 업로드 실패는 폴링을 막지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.poller", name = "enabled", havingValue = "true")
@Slf4j
public class PaRawArchiveUploader {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneOffset.UTC);
    private static final int MAX_TRACKED_GAMES = 64;
    private static final int PER_PAGE = 100;

    private final PollerProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<Long, String> lastResponseHashByGame = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
            return size() > MAX_TRACKED_GAMES;
        }
    };

    private S3Client s3Client;

    public PaRawArchiveUploader(PollerProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, null);
    }

    PaRawArchiveUploader(PollerProperties properties, ObjectMapper objectMapper, S3Client s3Client) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.s3Client = s3Client;
    }

    public void upload(long gameId, JsonNode response, Instant observedAt) {
        String bucket = properties.paArchive().bucket();
        if (bucket == null || bucket.isBlank() || response == null) {
            return;
        }
        try {
            String hash = md5(objectMapper.writeValueAsBytes(response));
            if (hash.equals(lastResponseHashByGame.get(gameId))) {
                return;
            }

            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("observed_at", observedAt.toString());
            envelope.put("endpoint", "/plate_appearances");
            ObjectNode params = envelope.putObject("params");
            params.put("game_id", String.valueOf(gameId));
            params.put("per_page", PER_PAGE);
            envelope.set("response", response);

            String key = "raw/plate_appearances/game_id=%d/pa_%s_%sZ.json.gz".formatted(
                    gameId,
                    DATE_FORMAT.format(observedAt),
                    TIME_FORMAT.format(observedAt)
            );
            client().putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/json")
                            .contentEncoding("gzip")
                            .build(),
                    RequestBody.fromBytes(gzip(objectMapper.writeValueAsBytes(envelope)))
            );
            lastResponseHashByGame.put(gameId, hash);
        } catch (Exception e) {
            log.warn("PA 원본 업로드 실패: gameId={}", gameId, e);
        }
    }

    private synchronized S3Client client() {
        if (s3Client == null) {
            S3ClientBuilder builder = S3Client.builder();
            String region = properties.paArchive().region();
            if (region != null && !region.isBlank()) {
                builder.region(Region.of(region));
            }
            s3Client = builder.build();
        }
        return s3Client;
    }

    private static byte[] gzip(byte[] bytes) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipStream = new GZIPOutputStream(out)) {
            gzipStream.write(bytes);
        }
        return out.toByteArray();
    }

    private static String md5(byte[] bytes) throws java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
    }
}
