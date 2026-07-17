package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class PaRawArchiveUploaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final S3Client s3Client = mock(S3Client.class);
    private final PaRawArchiveUploader uploader = new PaRawArchiveUploader(
            properties("archive-bucket"),
            objectMapper,
            s3Client
    );

    private final Instant observedAt = Instant.parse("2026-07-08T01:02:03Z");

    @Test
    void upload_shouldWriteRawLayoutKeyAndSkipUnchangedResponse() {
        ObjectNode response = objectMapper.createObjectNode();
        response.putArray("data").addObject().put("pa_number", 1);

        uploader.upload(100L, response, observedAt);
        uploader.upload(100L, response, observedAt.plusSeconds(20));
        ObjectNode changed = response.deepCopy();
        changed.withArray("data").addObject().put("pa_number", 2);
        uploader.upload(100L, changed, observedAt.plusSeconds(40));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(2)).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getAllValues().getFirst().bucket()).isEqualTo("archive-bucket");
        assertThat(requestCaptor.getAllValues().getFirst().key())
                .isEqualTo("raw/plate_appearances/game_id=100/pa_2026-07-08_010203Z.json.gz");
    }

    @Test
    void upload_shouldSkipWhenBucketMissing() {
        PaRawArchiveUploader disabled = new PaRawArchiveUploader(properties(null), objectMapper, s3Client);
        ObjectNode response = objectMapper.createObjectNode();

        disabled.upload(100L, response, observedAt);

        verify(s3Client, times(0)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    private static PollerProperties properties(String bucket) {
        return new PollerProperties(
                true,
                Duration.ofSeconds(20),
                Duration.ofSeconds(75),
                Duration.ofMinutes(10),
                Duration.ofMinutes(15),
                Duration.ofSeconds(20),
                0,
                0,
                9,
                5,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                1000,
                Duration.ofHours(1),
                Duration.ofMinutes(15),
                Duration.ofMinutes(30),
                10,
                new PollerProperties.PaArchive(bucket, "ap-northeast-2")
        );
    }
}
