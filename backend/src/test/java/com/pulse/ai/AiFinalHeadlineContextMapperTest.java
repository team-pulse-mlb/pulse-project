package com.pulse.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.FinalHeadlineContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiFinalHeadlineContextMapperTest {

    private final AiFinalHeadlineContextMapper mapper = new AiFinalHeadlineContextMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 공개_컨텍스트를_보호_키_없이_결과_기반_HTTP_요청으로_변환한다() throws Exception {
        FinalHeadlineContext context = new FinalHeadlineContext(
                5058990L,
                AiCopyMode.REVEALED,
                "STATUS_FINAL",
                "경기 종료",
                List.of(),
                List.of(),
                List.of(),
                new FinalHeadlineContext.Teams(
                        new FinalHeadlineContext.Team("Los Angeles Dodgers", "LAD"),
                        new FinalHeadlineContext.Team("San Francisco Giants", "SF")),
                new FinalHeadlineContext.FinalScore(5, 3),
                "home",
                10,
                true,
                false,
                List.of(new FinalHeadlineContext.RevealedMoment(
                        10,
                        "Bottom",
                        "LAD",
                        List.of("scoring_play", "home_run", "lead_change"),
                        "Shohei Ohtani",
                        2,
                        new FinalHeadlineContext.ScoreAfter(5, 3),
                        null)),
                "context-hash"
        );

        AiFinalHeadlineRequest request = mapper.toRequest(context);
        JsonNode safeContext = objectMapper.valueToTree(request.safeContext());

        assertThat(request.mode()).isEqualTo("REVEALED");
        assertThat(safeContext.has("safeTags")).isFalse();
        assertThat(safeContext.has("reasonCodes")).isFalse();
        assertThat(safeContext.has("keyMoments")).isFalse();
        assertThat(safeContext.path("teams").path("home").path("abbr").asText()).isEqualTo("LAD");
        assertThat(safeContext.path("finalScore").path("home").asInt()).isEqualTo(5);
        assertThat(safeContext.path("inningsPlayed").asInt()).isEqualTo(10);
        assertThat(safeContext.path("revealedMoments").get(0).path("batter").asText())
                .isEqualTo("Shohei Ohtani");
        assertThat(safeContext.path("revealedMoments").get(0).has("scoringPlays")).isFalse();
    }

    @Test
    void FINAL_HEADLINE_v2_공개_근거를_HTTP_요청으로_변환한다() throws Exception {
        Instant startTime = Instant.parse("2026-07-17T02:10:00Z");

        FinalHeadlineContext.PlayerInfo batter =
                new FinalHeadlineContext.PlayerInfo(660271L, "Shohei Ohtani");

        FinalHeadlineContext.PlayerInfo pitcher =
                new FinalHeadlineContext.PlayerInfo(657277L, "Logan Webb");

        Map<String, Object> eventEvidence = Map.of(
                "runsScored", 3,
                "scoreAfter", Map.of(
                        "home", 5,
                        "away", 3
                )
        );

        FinalHeadlineContext context = new FinalHeadlineContext(
                5058990L,
                AiCopyMode.REVEALED,
                "STATUS_FINAL",
                "경기 종료",
                List.of(),
                List.of(),
                List.of(),
                new FinalHeadlineContext.Teams(
                        new FinalHeadlineContext.Team("Los Angeles Dodgers", "LAD"),
                        new FinalHeadlineContext.Team("San Francisco Giants", "SF")
                ),
                new FinalHeadlineContext.FinalScore(5, 3),
                "home",
                10,
                true,
                false,
                List.of(new FinalHeadlineContext.RevealedMoment(
                        10,
                        "Bottom",
                        "LAD",
                        List.of("scoring_play", "home_run", "lead_change"),
                        "Shohei Ohtani",
                        3,
                        new FinalHeadlineContext.ScoreAfter(5, 3),
                        null
                )),
                "Dodger Stadium",
                startTime,
                List.of(0, 0, 1, 0, 0, 1, 0, 0, 0, 3),
                List.of(0, 0, 0, 0, 0, 0, 0, 2, 1, 0),
                new FinalHeadlineContext.SummaryFacts(
                        "home",
                        "Los Angeles Dodgers",
                        "San Francisco Giants",
                        5,
                        3,
                        "home",
                        3,
                        8,
                        10,
                        3,
                        2,
                        true,
                        true,
                        false,
                        true,
                        10,
                        2,
                        8
                ),
                List.of(new FinalHeadlineContext.RevealedEvent(
                        9001L,
                        "home_run",
                        10,
                        "Bottom",
                        batter,
                        pitcher,
                        eventEvidence
                )),
                List.of(new FinalHeadlineContext.VerifiedPlay(
                        7001L,
                        1136L,
                        10,
                        "Bottom",
                        "Shohei Ohtani homered to right center.",
                        "Shohei Ohtani, 우중간 3점 홈런",
                        5,
                        3,
                        true,
                        3,
                        1,
                        2,
                        1,
                        batter,
                        pitcher,
                        true,
                        true,
                        false,
                        List.of(
                                "SCORING_PLAY",
                                "DECISIVE_SCORE",
                                "HOME_RUN"
                        )
                )),
                "context-hash-v2"
        );

        AiFinalHeadlineRequest request = mapper.toRequest(context);
        JsonNode safeContext = objectMapper.valueToTree(request.safeContext());

        assertThat(request.gameId()).isEqualTo(5058990L);
        assertThat(request.mode()).isEqualTo("REVEALED");
        assertThat(request.contextHash()).isEqualTo("context-hash-v2");
        assertThat(request.safeContext())
                .isInstanceOf(AiFinalHeadlineRequest.RevealedSafeContext.class);

        assertThat(safeContext.path("venue").asText())
                .isEqualTo("Dodger Stadium");
        assertThat(safeContext.path("startTime").asText())
                .isEqualTo("2026-07-17T02:10:00Z");

        assertThat(safeContext.path("homeInningScores").size())
                .isEqualTo(10);
        assertThat(safeContext.path("homeInningScores").get(9).asInt())
                .isEqualTo(3);
        assertThat(safeContext.path("awayInningScores").get(8).asInt())
                .isEqualTo(1);

        JsonNode summaryFacts = safeContext.path("summaryFacts");
        assertThat(summaryFacts.path("winnerSide").asText())
                .isEqualTo("home");
        assertThat(summaryFacts.path("decisiveInning").asInt())
                .isEqualTo(10);
        assertThat(summaryFacts.path("walkOff").asBoolean())
                .isTrue();
        assertThat(summaryFacts.path("totalRuns").asInt())
                .isEqualTo(8);

        JsonNode revealedEvent = safeContext.path("revealedEvents").get(0);
        assertThat(revealedEvent.path("eventId").asLong())
                .isEqualTo(9001L);
        assertThat(revealedEvent.path("eventType").asText())
                .isEqualTo("home_run");
        assertThat(revealedEvent.path("batter").path("id").asLong())
                .isEqualTo(660271L);
        assertThat(revealedEvent.path("pitcher").path("name").asText())
                .isEqualTo("Logan Webb");
        assertThat(revealedEvent.path("evidence").path("runsScored").asInt())
                .isEqualTo(3);

        JsonNode verifiedPlay = safeContext.path("verifiedPlays").get(0);
        assertThat(verifiedPlay.path("playId").asLong())
                .isEqualTo(7001L);
        assertThat(verifiedPlay.path("translatedText").asText())
                .isEqualTo("Shohei Ohtani, 우중간 3점 홈런");
        assertThat(verifiedPlay.path("batter").path("name").asText())
                .isEqualTo("Shohei Ohtani");
        assertThat(verifiedPlay.path("factTags").get(2).asText())
                .isEqualTo("HOME_RUN");
    }
}
