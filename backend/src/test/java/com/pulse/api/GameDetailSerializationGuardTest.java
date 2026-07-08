package com.pulse.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.api.GameQueryService.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameDetailSerializationGuardTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void protectedDetailResponse_shouldNotSerializeSpoilerFields() {
        // given
        // 보호 모드 응답은 DTO 구조 자체에서 점수, play text, 득점 여부를 제외해야 한다.
        // 이 테스트는 나중에 누군가 protected DTO에 스포일러 필드를 추가하면 바로 실패하도록 막는 가드다.
        ProtectedGameDetailResponse response = new ProtectedGameDetailResponse(
                900001L,
                "STATUS_IN_PROGRESS",
                Instant.parse("2026-07-02T00:00:00Z"),

                // 보호 모드에서도 상단 매치업 표시를 위해 팀 정보는 직렬화된다.
                // 단, 점수와 결과성 필드는 여전히 포함하지 않아야 한다.
                new TeamResponse(1L, "Home Team", "HOM"),
                new TeamResponse(2L, "Away Team", "AWY"),

                "후반",
                new ProtectedSummaryResponse(List.of("후반 긴장 구간", "득점권 압박")),
                List.of(
                        new ProtectedPlayResponse(
                                "Pitch",
                                8,
                                "Top",
                                2,
                                3,
                                2
                        )
                ),

                // liveUpdateBlocks는 protected 모드에서도 내려가지만,
                // 점수, play text 같은 스포일러 필드를 포함하지 않는 안전한 블록이다.
                // DTO 생성자에 liveUpdateBlocks가 추가되었으므로 테스트에서도 같은 순서로 넘긴다.
                List.of(
                        new LiveUpdateBlockResponse(
                                "최근",
                                "진행 중",
                                "득점권 압박",
                                "긴장감 있는 흐름이 감지됐습니다.",
                                List.of("득점권 압박", "후반 긴장 구간")
                        )
                ),

                DisplayMode.PROTECTED
        );

        // when
        JsonNode json = objectMapper.valueToTree(response);
        JsonNode play = json.get("recentPlays").get(0);
        JsonNode homeTeam = json.get("homeTeam");
        JsonNode awayTeam = json.get("awayTeam");
        JsonNode liveUpdateBlock = json.get("liveUpdateBlocks").get(0);

        // then
        assertThat(json.get("displayMode").asText()).isEqualTo("PROTECTED");

        // 보호 모드 최상위 응답에는 상단 매치업 표시용 팀 정보는 포함될 수 있다.
        // 하지만 점수와 내부 점수 요약은 여전히 포함되면 안 된다.
        assertThat(json.has("homeTeam")).isTrue();
        assertThat(json.has("awayTeam")).isTrue();
        assertThat(json.has("score")).isFalse();
        assertThat(json.has("scoreSummary")).isFalse();

        // 보호 모드의 팀 정보는 id, name, abbr만 가져야 한다.
        // score 같은 점수성 필드가 팀 객체 안에 들어가면 안 된다.
        assertThat(homeTeam.has("id")).isTrue();
        assertThat(homeTeam.has("name")).isTrue();
        assertThat(homeTeam.has("abbr")).isTrue();
        assertThat(homeTeam.has("score")).isFalse();

        assertThat(awayTeam.has("id")).isTrue();
        assertThat(awayTeam.has("name")).isTrue();
        assertThat(awayTeam.has("abbr")).isTrue();
        assertThat(awayTeam.has("score")).isFalse();

        // 보호 모드 play 응답에는 결과를 직접 드러내는 필드가 없어야 한다.
        assertThat(play.has("text")).isFalse();
        assertThat(play.has("homeScore")).isFalse();
        assertThat(play.has("awayScore")).isFalse();
        assertThat(play.has("scoringPlay")).isFalse();
        assertThat(play.has("scoreValue")).isFalse();

        // liveUpdateBlocks는 protected 모드에서도 허용되지만,
        // 블록 내부에도 점수, play text 같은 스포일러 필드가 없어야 한다.
        assertThat(json.has("liveUpdateBlocks")).isTrue();
        assertThat(liveUpdateBlock.has("timeLabel")).isTrue();
        assertThat(liveUpdateBlock.has("periodLabel")).isTrue();
        assertThat(liveUpdateBlock.has("title")).isTrue();
        assertThat(liveUpdateBlock.has("description")).isTrue();
        assertThat(liveUpdateBlock.has("tags")).isTrue();

        assertThat(liveUpdateBlock.has("intensity")).isFalse();
        assertThat(liveUpdateBlock.has("score")).isFalse();
        assertThat(liveUpdateBlock.has("text")).isFalse();
        assertThat(liveUpdateBlock.has("homeScore")).isFalse();
        assertThat(liveUpdateBlock.has("awayScore")).isFalse();
        assertThat(liveUpdateBlock.has("scoringPlay")).isFalse();
        assertThat(liveUpdateBlock.has("scoreValue")).isFalse();

        // 대신 보호 모드에서 허용하는 흐름 정보는 남아 있어야 한다.
        assertThat(json.has("periodLabel")).isTrue();
        assertThat(json.has("summary")).isTrue();
        assertThat(json.has("recentPlays")).isTrue();
        assertThat(play.has("type")).isTrue();
        assertThat(play.has("inning")).isTrue();
        assertThat(play.has("inningType")).isTrue();
        assertThat(play.has("outs")).isTrue();
        assertThat(play.has("balls")).isTrue();
        assertThat(play.has("strikes")).isTrue();
    }

    @Test
    void revealedDetailResponse_shouldSerializeSpoilerFields() {
        // given
        // 공개 모드는 사용자가 직접 스포일러 공개를 선택한 상태다.
        // 따라서 팀명, 점수, play text, 득점 관련 필드를 포함해야 한다.
        RevealedGameDetailResponse response = new RevealedGameDetailResponse(
                900001L,
                "STATUS_IN_PROGRESS",
                Instant.parse("2026-07-02T00:00:00Z"),
                8,
                new TeamResponse(1L, "Home Team", "HOM"),
                new TeamResponse(2L, "Away Team", "AWY"),
                new ScoreResponse(3, 2),
                List.of(
                        new RevealedPlayResponse(
                                10L,
                                100L,
                                "Play Result",
                                8,
                                "Top",
                                "Sample revealed play text",
                                3,
                                2,
                                true,
                                1,
                                2,
                                3,
                                2,
                                Instant.parse("2026-07-02T00:02:00Z")
                        )
                ),

                // revealed 응답에도 liveUpdateBlocks가 포함된다.
                // 현재 블록 자체는 protected와 동일하게 spoiler-safe 구조를 유지한다.
                List.of(
                        new LiveUpdateBlockResponse(
                                "최근",
                                "진행 중",
                                "접전 흐름",
                                "긴장감 있는 흐름이 감지됐습니다.",
                                List.of("접전 흐름", "후반 긴장 구간")
                        )
                ),

                DisplayMode.REVEALED
        );

        // when
        JsonNode json = objectMapper.valueToTree(response);
        JsonNode play = json.get("recentPlays").get(0);
        JsonNode liveUpdateBlock = json.get("liveUpdateBlocks").get(0);

        // then
        assertThat(json.get("displayMode").asText()).isEqualTo("REVEALED");

        // 공개 모드 최상위 응답에는 팀명과 실제 경기 점수는 포함된다.
        assertThat(json.has("homeTeam")).isTrue();
        assertThat(json.has("awayTeam")).isTrue();
        assertThat(json.has("score")).isTrue();
        assertThat(json.has("scoreSummary")).isFalse();
        assertThat(json.has("watchScore")).isFalse();
        assertThat(json.has("baseScore")).isFalse();
        assertThat(json.has("signals")).isFalse();
        assertThat(json.has("signalContributions")).isFalse();

        // 공개 모드 play 응답에는 결과 설명과 점수 변화 정보가 포함되어야 한다.
        assertThat(play.has("text")).isTrue();
        assertThat(play.has("homeScore")).isTrue();
        assertThat(play.has("awayScore")).isTrue();
        assertThat(play.has("scoringPlay")).isTrue();
        assertThat(play.has("scoreValue")).isTrue();

        // revealed 응답에도 liveUpdateBlocks가 포함되어야 한다.
        assertThat(json.has("liveUpdateBlocks")).isTrue();
        assertThat(liveUpdateBlock.has("timeLabel")).isTrue();
        assertThat(liveUpdateBlock.has("periodLabel")).isTrue();
        assertThat(liveUpdateBlock.has("title")).isTrue();
        assertThat(liveUpdateBlock.has("description")).isTrue();
        assertThat(liveUpdateBlock.has("tags")).isTrue();
        assertThat(liveUpdateBlock.has("intensity")).isFalse();
    }
}