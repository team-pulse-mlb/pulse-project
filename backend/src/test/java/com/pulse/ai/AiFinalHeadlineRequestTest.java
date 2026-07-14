package com.pulse.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiFinalHeadlineRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void protectedRequestDoesNotSerializeFinalScoreAndWinnerKeys() throws Exception {
        AiFinalHeadlineRequest request = new AiFinalHeadlineRequest(
                5058990L,
                "PROTECTED",
                "final-headline-protected-hash",
                new AiFinalHeadlineRequest.ProtectedSafeContext(
                        "STATUS_FINAL",
                        "경기 종료",
                        List.of("후반 긴장 구간"),
                        List.of("late_or_extra"),
                        List.of(new AiFinalHeadlineRequest.KeyMoment(7, "만루 승부"))
                )
        );

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(request));
        JsonNode safeContext = root.get("safeContext");

        assertEquals(5058990L, root.get("gameId").asLong());
        assertEquals("PROTECTED", root.get("mode").asText());
        assertEquals("final-headline-protected-hash", root.get("contextHash").asText());

        // PROTECTED 요청에는 결과 관련 key 자체가 존재하면 안 됩니다.
        assertFalse(safeContext.has("finalScore"));
        assertFalse(safeContext.has("winner"));

        // 결과 외 safeContext 필드는 정상적으로 직렬화되어야 합니다.
        assertEquals("STATUS_FINAL", safeContext.get("gameStatus").asText());
        assertEquals("경기 종료", safeContext.get("inningPhase").asText());
        assertEquals("후반 긴장 구간", safeContext.get("safeTags").get(0).asText());
        assertEquals("late_or_extra", safeContext.get("reasonCodes").get(0).asText());
        assertEquals(7, safeContext.get("keyMoments").get(0).get("inning").asInt());
        assertEquals("만루 승부", safeContext.get("keyMoments").get(0).get("label").asText());
    }

    @Test
    void revealedRequestSerializesFinalScoreAndWinnerKeys() throws Exception {
        AiFinalHeadlineRequest request = new AiFinalHeadlineRequest(
                5058990L,
                "REVEALED",
                "final-headline-revealed-hash",
                new AiFinalHeadlineRequest.RevealedSafeContext(
                        "STATUS_FINAL",
                        "경기 종료",
                        List.of("후반 긴장 구간"),
                        List.of("late_or_extra"),
                        List.of(new AiFinalHeadlineRequest.KeyMoment(7, "만루 승부")),
                        new AiFinalHeadlineRequest.FinalScore(5, 3),
                        "home"
                )
        );

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(request));
        JsonNode safeContext = root.get("safeContext");

        assertEquals(5058990L, root.get("gameId").asLong());
        assertEquals("REVEALED", root.get("mode").asText());
        assertEquals("final-headline-revealed-hash", root.get("contextHash").asText());

        // REVEALED 요청에는 결과 관련 key가 존재해야 합니다.
        assertTrue(safeContext.has("finalScore"));
        assertTrue(safeContext.has("winner"));

        assertEquals(5, safeContext.get("finalScore").get("home").asInt());
        assertEquals(3, safeContext.get("finalScore").get("away").asInt());
        assertEquals("home", safeContext.get("winner").asText());
    }

    @Test
    void revealedDrawRequestKeepsWinnerKeyWithNullValue() throws Exception {
        AiFinalHeadlineRequest request = new AiFinalHeadlineRequest(
                5058990L,
                "REVEALED",
                "final-headline-revealed-draw-hash",
                new AiFinalHeadlineRequest.RevealedSafeContext(
                        "STATUS_FINAL",
                        "경기 종료",
                        List.of("후반 긴장 구간"),
                        List.of("late_or_extra"),
                        List.of(new AiFinalHeadlineRequest.KeyMoment(7, "만루 승부")),
                        new AiFinalHeadlineRequest.FinalScore(3, 3),
                        null
                )
        );

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(request));
        JsonNode safeContext = root.get("safeContext");

        // REVEALED 무승부에서는 winner=null 자체가 의미 있으므로 key를 유지해야 합니다.
        assertTrue(safeContext.has("winner"));
        assertTrue(safeContext.get("winner").isNull());
        assertEquals(3, safeContext.get("finalScore").get("home").asInt());
        assertEquals(3, safeContext.get("finalScore").get("away").asInt());
    }
}
