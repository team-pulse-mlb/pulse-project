package com.pulse.ai;

import com.pulse.api.GameQueryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiSafeContextMapperTest {

    private final AiSafeContextMapper mapper = new AiSafeContextMapper();

    @Test
    void mapsSpoilerFreeLlmContextResponseToAiSafeContext() {
        GameQueryService.SpoilerFreeLlmContextResponse response =
                new GameQueryService.SpoilerFreeLlmContextResponse(
                        5059082L,
                        GameQueryService.LlmPurpose.FINAL_HEADLINE,
                        "STATUS_FINAL",
                        Instant.parse("2026-07-08T12:00:00Z"),
                        "경기 종료",
                        List.of(
                                new GameQueryService.TeamResponse(1L, "Home Team", "HOM"),
                                new GameQueryService.TeamResponse(2L, "Away Team", "AWY")
                        ),
                        Arrays.asList(" 후반 긴장 구간 ", null, "", "득점권 압박"),
                        List.of("late_or_extra", " leverage "),
                        List.of()
                );

        AiSafeContext safeContext = mapper.toSafeContext(response);

        assertEquals("STATUS_FINAL", safeContext.gameStatus());
        assertEquals("경기 종료", safeContext.inningPhase());

        // AiSafeContext는 list 필드를 null 없이 정리하고, 빈 문자열과 앞뒤 공백을 제거한다.
        assertEquals(List.of("후반 긴장 구간", "득점권 압박"), safeContext.safeTags());
        assertEquals(List.of("late_or_extra", "leverage"), safeContext.reasonCodes());
    }

    @Test
    void mapsNullListsToEmptyLists() {
        GameQueryService.SpoilerFreeLlmContextResponse response =
                new GameQueryService.SpoilerFreeLlmContextResponse(
                        5059082L,
                        GameQueryService.LlmPurpose.REPLAY_SUMMARY,
                        "STATUS_FINAL",
                        Instant.parse("2026-07-08T12:00:00Z"),
                        "경기 종료",
                        List.of(),
                        null,
                        null,
                        List.of()
                );

        AiSafeContext safeContext = mapper.toSafeContext(response);

        // ai-service 요청을 단순하게 만들기 위해 list 필드는 항상 빈 리스트로 보정된다.
        assertEquals(List.of(), safeContext.safeTags());
        assertEquals(List.of(), safeContext.reasonCodes());
    }

    @Test
    void rejectsNullResponse() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> mapper.toSafeContext(null)
        );

        assertEquals("response must not be null", exception.getMessage());
    }
}