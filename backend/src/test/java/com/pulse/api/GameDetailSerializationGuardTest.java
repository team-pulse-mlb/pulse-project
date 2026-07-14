package com.pulse.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.api.GameQueryService.*;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class GameDetailSerializationGuardTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    @Test
    void scheduledDetail_shouldExposeOnlyPregameFields() {
        ScheduledGameDetailResponse response =
                new ScheduledGameDetailResponse(
                        5059999L,
                        "STATUS_SCHEDULED",
                        DisplayMode.PROTECTED,
                        new TeamResponse(
                                28L,
                                "Texas Rangers",
                                "TEX"),
                        new TeamResponse(
                                10L,
                                "Detroit Tigers",
                                "DET"),
                        Instant.parse(
                                "2026-07-15T00:05:00Z"),
                        "Globe Life Field",
                        new ProbablePitchersResponse(
                                "Home Starter",
                                "Away Starter"));

        JsonNode json =
                objectMapper.valueToTree(response);

        SoftAssertions.assertSoftly(
                softly -> {
                    softly.assertThat(
                                    json.get("status").asText())
                            .isEqualTo("STATUS_SCHEDULED");

                    /*
                     * 예정 경기에는 공개할 결과가 없으므로
                     * 요청 모드와 관계없이 보호 상태로 응답한다.
                     */
                    softly.assertThat(
                                    json.get("displayMode").asText())
                            .isEqualTo("PROTECTED");

                    /*
                     * 예정 상세에서 사용하는 기본 경기 정보다.
                     */
                    softly.assertThat(json.has("homeTeam"))
                            .isTrue();
                    softly.assertThat(json.has("awayTeam"))
                            .isTrue();
                    softly.assertThat(json.has("startTime"))
                            .isTrue();
                    softly.assertThat(json.has("venue"))
                            .isTrue();
                    softly.assertThat(json.has("probablePitchers"))
                            .isTrue();

                    JsonNode probablePitchers =
                            json.get("probablePitchers");

                    softly.assertThat(
                                    probablePitchers.get("home").asText())
                            .isEqualTo("Home Starter");

                    softly.assertThat(
                                    probablePitchers.get("away").asText())
                            .isEqualTo("Away Starter");

                    /*
                     * 예정 경기에는 진행 경기의 현재 상황이나
                     * 종료 경기의 결과 정보를 포함하지 않는다.
                     */
                    softly.assertThat(json.has("score"))
                            .isFalse();
                    softly.assertThat(json.has("finalScore"))
                            .isFalse();
                    softly.assertThat(json.has("periodLabel"))
                            .isFalse();
                    softly.assertThat(json.has("inning"))
                            .isFalse();
                    softly.assertThat(json.has("inningType"))
                            .isFalse();
                    softly.assertThat(json.has("situation"))
                            .isFalse();
                    softly.assertThat(json.has("currentMatchup"))
                            .isFalse();
                    softly.assertThat(json.has("inningScores"))
                            .isFalse();
                    softly.assertThat(json.has("headline"))
                            .isFalse();
                    softly.assertThat(json.has("scoringSummary"))
                            .isFalse();
                    softly.assertThat(json.has("tensionCurve"))
                            .isFalse();
                    softly.assertThat(
                                    json.has("favoritePlayersPlaying"))
                            .isFalse();
                    softly.assertThat(
                                    json.has("switchSuggestion"))
                            .isFalse();

                    /*
                     * 이벤트 목록은 별도의 /events API가 담당한다.
                     */
                    softly.assertThat(json.has("summary"))
                            .isFalse();
                    softly.assertThat(json.has("recentPlays"))
                            .isFalse();

                    assertInternalRecommendationFieldsAreAbsent(
                            softly,
                            json);
                });
    }

    @Test
    void protectedLiveDetail_shouldFollowLatestSpoilerContract() {
        ProtectedGameDetailResponse response =
                new ProtectedGameDetailResponse(
                        900001L,
                        "STATUS_IN_PROGRESS",
                        DisplayMode.PROTECTED,
                        new TeamResponse(
                                1L,
                                "Chicago Cubs",
                                "CHC"),
                        new TeamResponse(
                                2L,
                                "San Diego Padres",
                                "SD"),
                        Instant.parse(
                                "2026-07-02T00:00:00Z"),
                        "후반",
                        8,
                        new SituationResponse(
                                2,
                                3,
                                2,
                                false,
                                true,
                                false,
                                true,
                                false),
                        List.of("Shohei Ohtani"),
                        null);

        JsonNode json =
                objectMapper.valueToTree(response);

        SoftAssertions.assertSoftly(
                softly -> {
                    softly.assertThat(
                                    json.get("displayMode").asText())
                            .isEqualTo("PROTECTED");

                    /*
                     * 팀 정보와 이닝 숫자, 현재 상황은
                     * 보호 모드에서도 허용한다.
                     */
                    softly.assertThat(json.has("homeTeam"))
                            .isTrue();
                    softly.assertThat(json.has("awayTeam"))
                            .isTrue();
                    softly.assertThat(json.has("inning"))
                            .isTrue();
                    softly.assertThat(json.has("situation"))
                            .isTrue();
                    softly.assertThat(
                                    json.has("favoritePlayersPlaying"))
                            .isTrue();
                    softly.assertThat(
                                    json.has("switchSuggestion"))
                            .isTrue();

                    /*
                     * 초·말 정보는 보호 응답의 어느 위치에도
                     * 포함되어서는 안 된다.
                     */
                    softly.assertThat(
                                    json.findValue("inningType"))
                            .isNull();

                    /*
                     * 점수와 현재 타석 선수는 공개 모드 전용이다.
                     */
                    softly.assertThat(json.has("score"))
                            .isFalse();
                    softly.assertThat(json.has("currentMatchup"))
                            .isFalse();
                    softly.assertThat(json.has("inningScores"))
                            .isFalse();

                    /*
                     * 이벤트 목록은 별도의 /events API가 담당한다.
                     */
                    softly.assertThat(json.has("summary"))
                            .isFalse();
                    softly.assertThat(json.has("recentPlays"))
                            .isFalse();

                    assertInternalRecommendationFieldsAreAbsent(
                            softly,
                            json);
                });
    }

    @Test
    void revealedLiveDetail_shouldFollowLatestPublicContract() {
        RevealedGameDetailResponse response =
                new RevealedGameDetailResponse(
                        900001L,
                        "STATUS_IN_PROGRESS",
                        DisplayMode.REVEALED,
                        new TeamResponse(
                                1L,
                                "Chicago Cubs",
                                "CHC"),
                        new TeamResponse(
                                2L,
                                "San Diego Padres",
                                "SD"),
                        Instant.parse(
                                "2026-07-02T00:00:00Z"),
                        new ScoreResponse(
                                3,
                                4),
                        8,
                        "Top",
                        new SituationResponse(
                                2,
                                3,
                                2,
                                false,
                                true,
                                false,
                                true,
                                false),
                        new CurrentMatchupResponse(
                                new PlayerResponse(
                                        1001L,
                                        "Sample Batter"),
                                new PlayerResponse(
                                        2001L,
                                        "Sample Pitcher")),
                        List.of("Sample Batter"),
                        new InningScoresResponse(
                                List.of(
                                        0,
                                        1,
                                        0,
                                        2,
                                        0,
                                        0,
                                        1,
                                        0),
                                List.of(
                                        0,
                                        0,
                                        1,
                                        0,
                                        2,
                                        0,
                                        0,
                                        0)));

        JsonNode json =
                objectMapper.valueToTree(response);

        SoftAssertions.assertSoftly(
                softly -> {
                    softly.assertThat(
                                    json.get("displayMode").asText())
                            .isEqualTo("REVEALED");

                    /*
                     * 진행 공개 상세에서 사용하는 경기 정보다.
                     */
                    softly.assertThat(json.has("homeTeam"))
                            .isTrue();
                    softly.assertThat(json.has("awayTeam"))
                            .isTrue();
                    softly.assertThat(json.has("score"))
                            .isTrue();
                    softly.assertThat(json.has("inning"))
                            .isTrue();
                    softly.assertThat(json.has("inningType"))
                            .isTrue();
                    softly.assertThat(json.has("situation"))
                            .isTrue();
                    softly.assertThat(json.has("currentMatchup"))
                            .isTrue();
                    softly.assertThat(json.has("inningScores"))
                            .isTrue();
                    softly.assertThat(
                                    json.has("favoritePlayersPlaying"))
                            .isTrue();

                    /*
                     * 이벤트 목록은 상세 응답에 포함하지 않는다.
                     */
                    softly.assertThat(json.has("summary"))
                            .isFalse();
                    softly.assertThat(json.has("recentPlays"))
                            .isFalse();

                    assertInternalRecommendationFieldsAreAbsent(
                            softly,
                            json);
                });
    }

    @Test
    void protectedFinalDetail_shouldHideResultFields() {
        ProtectedFinalGameDetailResponse response =
                new ProtectedFinalGameDetailResponse(
                        5059082L,
                        "STATUS_FINAL",
                        DisplayMode.PROTECTED,
                        new TeamResponse(
                                28L,
                                "Texas Rangers",
                                "TEX"),
                        new TeamResponse(
                                10L,
                                "Detroit Tigers",
                                "DET"),
                        Instant.parse(
                                "2026-07-03T00:05:00Z"),

                        /*
                         * 종료 헤드라인은 아직 생성되지 않았을 수 있다.
                         * 이 경우 null을 정상 응답으로 허용한다.
                         */
                        null,
                        List.of(
                                new ProtectedTensionPointResponse(
                                        7,
                                        5)));

        JsonNode json =
                objectMapper.valueToTree(response);

        SoftAssertions.assertSoftly(
                softly -> {
                    softly.assertThat(
                                    json.get("displayMode").asText())
                            .isEqualTo("PROTECTED");

                    softly.assertThat(
                                    json.get("status").asText())
                            .isEqualTo("STATUS_FINAL");

                    softly.assertThat(json.has("homeTeam"))
                            .isTrue();
                    softly.assertThat(json.has("awayTeam"))
                            .isTrue();
                    softly.assertThat(json.has("headline"))
                            .isTrue();
                    softly.assertThat(
                                    json.get("headline").isNull())
                            .isTrue();
                    softly.assertThat(json.has("tensionCurve"))
                            .isTrue();

                    /*
                     * 보호용 경기 흐름은 이닝 숫자와 단계만 포함한다.
                     */
                    JsonNode tensionPoint =
                            json.get("tensionCurve").get(0);

                    softly.assertThat(
                                    tensionPoint.has("inning"))
                            .isTrue();
                    softly.assertThat(
                                    tensionPoint.has("level"))
                            .isTrue();
                    softly.assertThat(
                                    tensionPoint.has("inningType"))
                            .isFalse();

                    /*
                     * 종료 보호 응답에는 경기 결과를 나타내는
                     * 필드를 포함하지 않는다.
                     */
                    softly.assertThat(json.has("finalScore"))
                            .isFalse();
                    softly.assertThat(json.has("score"))
                            .isFalse();
                    softly.assertThat(json.has("inningScores"))
                            .isFalse();
                    softly.assertThat(json.has("scoringSummary"))
                            .isFalse();

                    /*
                     * 진행 경기 전용 필드도 종료 응답에는 없다.
                     */
                    softly.assertThat(json.has("periodLabel"))
                            .isFalse();
                    softly.assertThat(json.has("inning"))
                            .isFalse();
                    softly.assertThat(json.has("inningType"))
                            .isFalse();
                    softly.assertThat(json.has("situation"))
                            .isFalse();
                    softly.assertThat(json.has("currentMatchup"))
                            .isFalse();
                    softly.assertThat(
                                    json.has("favoritePlayersPlaying"))
                            .isFalse();
                    softly.assertThat(
                                    json.has("switchSuggestion"))
                            .isFalse();

                    softly.assertThat(json.has("summary"))
                            .isFalse();
                    softly.assertThat(json.has("recentPlays"))
                            .isFalse();

                    assertInternalRecommendationFieldsAreAbsent(
                            softly,
                            json);
                });
    }

    @Test
    void revealedFinalDetail_shouldExposeOnlyFinalResultFields() {
        RevealedFinalGameDetailResponse response =
                new RevealedFinalGameDetailResponse(
                        5059082L,
                        "STATUS_FINAL",
                        DisplayMode.REVEALED,
                        new TeamResponse(
                                28L,
                                "Texas Rangers",
                                "TEX"),
                        new TeamResponse(
                                10L,
                                "Detroit Tigers",
                                "DET"),
                        Instant.parse(
                                "2026-07-03T00:05:00Z"),
                        "Texas Rangers가 10-4로 승리한 경기입니다.",
                        new ScoreResponse(
                                10,
                                4),
                        new InningScoresResponse(
                                List.of(
                                        0,
                                        0,
                                        0,
                                        0,
                                        3,
                                        0,
                                        0,
                                        1,
                                        0),
                                List.of(
                                        0,
                                        3,
                                        0,
                                        2,
                                        0,
                                        1,
                                        3,
                                        1)),
                        List.of(
                                new ScoringPlayResponse(
                                        2,
                                        "Bottom",
                                        "Díaz homered to left center.")),
                        List.of(
                                new RevealedTensionPointResponse(
                                        8,
                                        "Bottom",
                                        5)));

        JsonNode json =
                objectMapper.valueToTree(response);

        SoftAssertions.assertSoftly(
                softly -> {
                    softly.assertThat(
                                    json.get("displayMode").asText())
                            .isEqualTo("REVEALED");

                    softly.assertThat(
                                    json.get("status").asText())
                            .isEqualTo("STATUS_FINAL");

                    softly.assertThat(json.has("homeTeam"))
                            .isTrue();
                    softly.assertThat(json.has("awayTeam"))
                            .isTrue();
                    softly.assertThat(json.has("headline"))
                            .isTrue();
                    softly.assertThat(json.has("finalScore"))
                            .isTrue();
                    softly.assertThat(json.has("inningScores"))
                            .isTrue();
                    softly.assertThat(json.has("scoringSummary"))
                            .isTrue();
                    softly.assertThat(json.has("tensionCurve"))
                            .isTrue();

                    /*
                     * 득점 플레이는 이닝, 초·말, 원문을 포함한다.
                     */
                    JsonNode scoringPlay =
                            json.get("scoringSummary").get(0);

                    softly.assertThat(
                                    scoringPlay.get("inning").asInt())
                            .isEqualTo(2);

                    softly.assertThat(
                                    scoringPlay.get("inningType").asText())
                            .isEqualTo("Bottom");

                    softly.assertThat(
                                    scoringPlay.get("text").asText())
                            .contains("Díaz");

                    /*
                     * 공개용 경기 흐름은 하프이닝 단위를 허용한다.
                     */
                    JsonNode tensionPoint =
                            json.get("tensionCurve").get(0);

                    softly.assertThat(
                                    tensionPoint.has("inning"))
                            .isTrue();
                    softly.assertThat(
                                    tensionPoint.has("inningType"))
                            .isTrue();
                    softly.assertThat(
                                    tensionPoint.has("level"))
                            .isTrue();

                    /*
                     * 종료 공개 응답에는 진행 경기의 현재 상황이나
                     * 현재 타석을 포함하지 않는다.
                     */
                    softly.assertThat(json.has("score"))
                            .isFalse();
                    softly.assertThat(json.has("periodLabel"))
                            .isFalse();
                    softly.assertThat(json.has("inning"))
                            .isFalse();
                    softly.assertThat(json.has("inningType"))
                            .isFalse();
                    softly.assertThat(json.has("situation"))
                            .isFalse();
                    softly.assertThat(json.has("currentMatchup"))
                            .isFalse();
                    softly.assertThat(
                                    json.has("favoritePlayersPlaying"))
                            .isFalse();
                    softly.assertThat(
                                    json.has("switchSuggestion"))
                            .isFalse();

                    softly.assertThat(json.has("summary"))
                            .isFalse();
                    softly.assertThat(json.has("recentPlays"))
                            .isFalse();

                    assertInternalRecommendationFieldsAreAbsent(
                            softly,
                            json);
                });
    }

    /**
     * 내부 추천 계산값은 사용자가 공개 모드를 선택했더라도
     * 외부 경기 상세 API 응답에 포함하지 않는다.
     */
    private static void assertInternalRecommendationFieldsAreAbsent(
            SoftAssertions softly,
            JsonNode json) {

        softly.assertThat(
                        json.findValue("scoreSummary"))
                .isNull();

        softly.assertThat(
                        json.findValue("baseScore"))
                .isNull();

        softly.assertThat(
                        json.findValue("watchScore"))
                .isNull();

        softly.assertThat(
                        json.findValue("pregameScore"))
                .isNull();

        softly.assertThat(
                        json.findValue("peakBaseScore"))
                .isNull();

        softly.assertThat(
                        json.findValue("signals"))
                .isNull();

        softly.assertThat(
                        json.findValue("signalContributions"))
                .isNull();

        softly.assertThat(
                        json.findValue("reasonTags"))
                .isNull();
    }
}