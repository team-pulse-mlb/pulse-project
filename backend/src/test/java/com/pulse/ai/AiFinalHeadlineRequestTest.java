package com.pulse.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

        // PROTECTED 요청에는 REVEALED v2 공개 근거도 존재하면 안 됩니다.
        assertFalse(safeContext.has("summaryFacts"));
        assertFalse(safeContext.has("revealedEvents"));
        assertFalse(safeContext.has("verifiedPlays"));
        assertFalse(safeContext.has("homeInningScores"));
        assertFalse(safeContext.has("awayInningScores"));

        // 결과 외 safeContext 필드는 정상적으로 직렬화되어야 합니다.
        assertEquals("STATUS_FINAL", safeContext.get("gameStatus").asText());
        assertEquals("경기 종료", safeContext.get("inningPhase").asText());
        assertEquals("후반 긴장 구간", safeContext.get("safeTags").get(0).asText());
        assertEquals("late_or_extra", safeContext.get("reasonCodes").get(0).asText());
        assertEquals(7, safeContext.get("keyMoments").get(0).get("inning").asInt());
        assertEquals("만루 승부", safeContext.get("keyMoments").get(0).get("label").asText());
    }

    @Test
    void revealedRequestSerializesFinalScoreWinnerAndV2ContextKeys() throws Exception {
        AiFinalHeadlineRequest request = new AiFinalHeadlineRequest(
                5058990L,
                "REVEALED",
                "final-headline-revealed-hash",
                new AiFinalHeadlineRequest.RevealedSafeContext(
                        "STATUS_FINAL",
                        "경기 종료",
                        new AiFinalHeadlineRequest.Teams(
                                new AiFinalHeadlineRequest.Team("Los Angeles Dodgers", "LAD"),
                                new AiFinalHeadlineRequest.Team("San Francisco Giants", "SF")),
                        new AiFinalHeadlineRequest.FinalScore(5, 3),
                        "home",
                        10,
                        true,
                        false,
                        List.of(new AiFinalHeadlineRequest.RevealedMoment(
                                10, "Bottom", "LAD",
                                List.of("scoring_play", "home_run", "lead_change"),
                                "Shohei Ohtani", 2,
                                new AiFinalHeadlineRequest.ScoreAfter(5, 3), null)),

                        "Dodger Stadium",
                        "2026-07-03T00:05:00Z",
                        List.of(0, 0, 1, 0, 0, 0, 0, 2, 2, 0),
                        List.of(1, 0, 0, 0, 0, 0, 0, 2, 0, 0),
                        new AiFinalHeadlineRequest.SummaryFacts(
                                "home",
                                "Los Angeles Dodgers",
                                "San Francisco Giants",
                                5,
                                3,

                                "away",
                                1,

                                8,
                                10,
                                2,

                                1,
                                true,
                                false,
                                false,
                                true,
                                10,

                                2,
                                8
                        ),
                        List.of(new AiFinalHeadlineRequest.RevealedEvent(
                                91L,
                                "home_run",
                                10,
                                "Bottom",
                                new AiFinalHeadlineRequest.PlayerInfo(660271L, "Shohei Ohtani"),
                                null,
                                Map.of("scoreValue", 2)
                        )),
                        List.of(new AiFinalHeadlineRequest.VerifiedPlay(
                                312L,
                                4250312L,

                                10,
                                "Bottom",

                                "Ohtani homered to right center.",
                                "Ohtani, 우중간 홈런",

                                5,
                                3,

                                true,
                                2,

                                1,
                                2,
                                1,

                                new AiFinalHeadlineRequest.PlayerInfo(660271L, "Shohei Ohtani"),
                                null,

                                false,
                                true,
                                false,

                                List.of("SCORING_PLAY", "RUNS_SCORED", "TRANSLATED")
                        ))
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
        assertFalse(safeContext.has("safeTags"));
        assertFalse(safeContext.has("reasonCodes"));
        assertFalse(safeContext.has("keyMoments"));

        assertEquals(5, safeContext.get("finalScore").get("home").asInt());
        assertEquals(3, safeContext.get("finalScore").get("away").asInt());
        assertEquals("home", safeContext.get("winner").asText());
        assertEquals("LAD", safeContext.get("teams").get("home").get("abbr").asText());
        assertEquals(10, safeContext.get("inningsPlayed").asInt());
        assertTrue(safeContext.get("extraInnings").asBoolean());

        // 기존 revealedMoments 직렬화는 유지되어야 합니다.
        assertEquals("Shohei Ohtani",
                safeContext.get("revealedMoments").get(0).get("batter").asText());
        assertFalse(safeContext.get("revealedMoments").get(0).has("scoringPlays"));

        // FINAL_HEADLINE v2 확장 context가 실제 HTTP JSON에 포함되어야 합니다.
        assertEquals("Dodger Stadium", safeContext.get("venue").asText());
        assertEquals("2026-07-03T00:05:00Z", safeContext.get("startTime").asText());
        assertEquals(0, safeContext.get("homeInningScores").get(0).asInt());
        assertEquals(1, safeContext.get("awayInningScores").get(0).asInt());

        JsonNode summaryFacts = safeContext.get("summaryFacts");
        assertEquals("home", summaryFacts.get("winnerSide").asText());
        assertEquals("Los Angeles Dodgers", summaryFacts.get("winnerName").asText());
        assertEquals("San Francisco Giants", summaryFacts.get("loserName").asText());
        assertEquals(5, summaryFacts.get("winnerScore").asInt());
        assertEquals(3, summaryFacts.get("loserScore").asInt());
        assertTrue(summaryFacts.get("comebackWin").asBoolean());
        assertTrue(summaryFacts.get("extraInnings").asBoolean());
        assertEquals(10, summaryFacts.get("finalInning").asInt());

        JsonNode revealedEvent = safeContext.get("revealedEvents").get(0);
        assertEquals(91L, revealedEvent.get("eventId").asLong());
        assertEquals("home_run", revealedEvent.get("eventType").asText());
        assertEquals("Shohei Ohtani", revealedEvent.get("batter").get("name").asText());
        assertEquals(2, revealedEvent.get("evidence").get("scoreValue").asInt());

        JsonNode verifiedPlay = safeContext.get("verifiedPlays").get(0);
        assertEquals(312L, verifiedPlay.get("playId").asLong());
        assertEquals("Ohtani homered to right center.", verifiedPlay.get("sourceText").asText());
        assertEquals("Ohtani, 우중간 홈런", verifiedPlay.get("translatedText").asText());
        assertEquals(5, verifiedPlay.get("homeScoreAfter").asInt());
        assertEquals(3, verifiedPlay.get("awayScoreAfter").asInt());
        assertTrue(verifiedPlay.get("scoringPlay").asBoolean());
        assertEquals("TRANSLATED", verifiedPlay.get("factTags").get(2).asText());
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
                        new AiFinalHeadlineRequest.Teams(
                                new AiFinalHeadlineRequest.Team("Home", "HOM"),
                                new AiFinalHeadlineRequest.Team("Away", "AWY")),
                        new AiFinalHeadlineRequest.FinalScore(3, 3),
                        null,
                        9,
                        false,
                        false,
                        List.of(),

                        null,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        List.of()
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
