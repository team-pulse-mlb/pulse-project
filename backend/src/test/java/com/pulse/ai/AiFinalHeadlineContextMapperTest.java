package com.pulse.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.FinalHeadlineContext;
import java.util.List;
import org.junit.jupiter.api.Test;

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
}
