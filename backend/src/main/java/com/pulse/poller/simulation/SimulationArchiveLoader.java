package com.pulse.poller.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.client.BdlDtos.BdlPlay;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/** 설정된 S3 원본에서 시뮬레이션 경기와 플레이를 우선 로드한다. */
@Component
@ConditionalOnProperty(prefix = "pulse.simulation", name = "enabled", havingValue = "true")
@Slf4j
public class SimulationArchiveLoader {
    private final SimulationProperties properties;
    private final ObjectMapper objectMapper;

    public SimulationArchiveLoader(SimulationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<ArchiveGame> load() {
        if (properties.s3() == null || properties.s3().bucket() == null || properties.s3().bucket().isBlank()
                || properties.archiveDate() == null || properties.archiveDate().isBlank()) {
            return Optional.empty();
        }
        try (S3Client s3 = s3Client()) {
            List<String> keys = new ArrayList<>();
            keys.addAll(listKeys(s3, "raw/games/dt=" + properties.archiveDate().trim() + "/"));
            keys.addAll(listKeys(s3, "raw/plays/game_id=" + properties.requiredSourceGameId() + "/"));
            keys.addAll(listKeys(s3, "raw/backfill/plays/game_id=" + properties.requiredSourceGameId() + "/"));
            keys.sort(Comparator.naturalOrder());

            BdlGame game = null;
            TreeMap<Long, BdlPlay> plays = new TreeMap<>();
            for (String key : keys) {
                JsonNode root = read(s3, key);
                String endpoint = root.path("endpoint").asText("");
                for (JsonNode node : data(root.path("response"))) {
                    if ("/games".equals(endpoint)) {
                        BdlGame candidate = objectMapper.convertValue(node, BdlGame.class);
                        if (candidate.id() == properties.requiredSourceGameId()) game = candidate;
                    } else if ("/plays".equals(endpoint)) {
                        BdlPlay play = objectMapper.convertValue(node, BdlPlay.class);
                        if (play.order() != null) plays.put(play.order(), play);
                    }
                }
            }
            if (game == null || plays.isEmpty()) return Optional.empty();
            log.info("simulation archive loaded: gameId={}, plays={}, bucket={}", game.id(), plays.size(), properties.s3().bucket());
            return Optional.of(new ArchiveGame(game, List.copyOf(plays.values())));
        } catch (RuntimeException e) {
            log.warn("simulation S3 archive unavailable; falling back to database: gameId={}", properties.sourceGameId(), e);
            return Optional.empty();
        }
    }

    private S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder();
        if (properties.s3().region() != null && !properties.s3().region().isBlank()) builder.region(Region.of(properties.s3().region()));
        return builder.build();
    }

    private List<String> listKeys(S3Client s3, String prefix) {
        List<String> keys = new ArrayList<>();
        for (var object : s3.listObjectsV2Paginator(request -> request.bucket(properties.s3().bucket()).prefix(prefix)).contents()) {
            if (object.key().endsWith(".json.gz")) keys.add(object.key());
            if (keys.size() >= properties.maxArchiveObjects()) break;
        }
        return keys;
    }

    private JsonNode read(S3Client s3, String key) {
        try {
            byte[] compressed = s3.getObject(request -> request.bucket(properties.s3().bucket()).key(key), ResponseTransformer.toBytes()).asByteArray();
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                return objectMapper.readTree(gzip);
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to read simulation archive: " + key, e);
        }
    }

    private static List<JsonNode> data(JsonNode response) {
        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray()) return List.of();
        List<JsonNode> nodes = new ArrayList<>();
        data.forEach(nodes::add);
        return nodes;
    }

    public record ArchiveGame(BdlGame game, List<BdlPlay> plays) {}
}
