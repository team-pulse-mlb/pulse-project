package com.pulse.api.gamedetail;

import com.pulse.api.gamedetail.GameEventQueryService.GameEventsResponse;
import com.pulse.api.gamedetail.GameEventQueryService.ProtectedEventResponse;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.GameRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GameEventQueryServiceTest {

    private final GameRepository gameRepository =
            mock(GameRepository.class);

    private final GameEventRepository gameEventRepository =
            mock(GameEventRepository.class);

    /*
     * 공개 모드 이벤트 조회는 폐기되었으므로
     * 서비스는 경기 존재 여부와 보호 하이라이트 조회에 필요한
     * 두 저장소만 의존한다.
     */
    private final GameEventQueryService service =
            new GameEventQueryService(
                    gameRepository,
                    gameEventRepository);

    @Test
    void protectedMode_shouldReturnOnlyTimelineHighlights() {
        // given
        long gameId = 100L;

        GameEvent highlightedEvent =
                event(
                        1L,
                        gameId,
                        "pressure_bases_loaded",
                        GameEvent.SPOILER_PROTECTED_SAFE,
                        Instant.parse("2026-07-14T10:00:00Z"));

        /*
         * 실제 저장 데이터에서도 급변 순간으로 선택된 이벤트만
         * 보호 모드 경기 흐름 조회 결과에 포함된다.
         */
        highlightedEvent.setTimelineHighlight(true);

        /*
         * 엔티티에 공개 모드용 정보가 들어 있어도
         * 보호 응답 DTO에는 초·말, 선수 ID, 근거 수치가
         * 포함되지 않아야 한다.
         */
        highlightedEvent.setInningType("Bottom");
        highlightedEvent.setBatterId(10L);
        highlightedEvent.setPitcherId(20L);
        highlightedEvent.setPayload(
                Map.of(
                        "outs", 2,
                        "balls", 3,
                        "strikes", 2));

        when(gameRepository.existsById(gameId))
                .thenReturn(true);

        when(
                gameEventRepository
                        .findByGameIdAndSpoilerLevelAndTimelineHighlightTrueOrderByObservedAtAscIdAsc(
                                gameId,
                                GameEvent.SPOILER_PROTECTED_SAFE))
                .thenReturn(
                        List.of(highlightedEvent));

        // when
        GameEventsResponse response =
                service.getEvents(
                        gameId,
                        "PROTECTED");

        // then
        assertThat(response.events())
                .singleElement()
                .isInstanceOfSatisfying(
                        ProtectedEventResponse.class,
                        event -> {
                            assertThat(event.eventId())
                                    .isEqualTo(1L);

                            assertThat(event.eventType())
                                    .isEqualTo(
                                            "pressure_bases_loaded");

                            assertThat(event.inning())
                                    .isEqualTo(7);

                            assertThat(event.label())
                                    .isEqualTo("만루 승부");

                            assertThat(event.copy())
                                    .isEqualTo("보호 모드 문구");
                        });

        /*
         * 보호 모드는 PROTECTED_SAFE 전체가 아니라
         * timelineHighlight=true 이벤트 전용 조회를 사용해야 한다.
         */
        verify(
                gameEventRepository,
                never())
                .findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                        gameId,
                        GameEvent.SPOILER_PROTECTED_SAFE);
    }

    @Test
    void revealedMode_shouldReturnEmptyWithoutReadingEventData() {
        // given
        long gameId = 200L;

        when(gameRepository.existsById(gameId))
                .thenReturn(true);

        // when
        GameEventsResponse response =
                service.getEvents(
                        gameId,
                        " revealed ");

        // then
        assertThat(response.events())
                .isEmpty();

        /*
         * 공개 모드의 경기 흐름은 최근 플레이 API만 사용한다.
         * 이벤트 API는 빈 목록을 즉시 반환해야 하므로
         * 이벤트 저장소를 조회하지 않는다.
         */
        verifyNoInteractions(gameEventRepository);
    }

    @Test
    void invalidMode_shouldFallBackToProtectedHighlightQuery() {
        // given
        long gameId = 300L;

        GameEvent highlightedEvent =
                event(
                        30L,
                        gameId,
                        "long_at_bat",
                        GameEvent.SPOILER_PROTECTED_SAFE,
                        Instant.parse("2026-07-14T11:00:00Z"));

        highlightedEvent.setTimelineHighlight(true);

        when(gameRepository.existsById(gameId))
                .thenReturn(true);

        when(
                gameEventRepository
                        .findByGameIdAndSpoilerLevelAndTimelineHighlightTrueOrderByObservedAtAscIdAsc(
                                gameId,
                                GameEvent.SPOILER_PROTECTED_SAFE))
                .thenReturn(
                        List.of(highlightedEvent));

        // when
        GameEventsResponse response =
                service.getEvents(
                        gameId,
                        "invalid-mode");

        // then
        assertThat(response.events())
                .singleElement()
                .isInstanceOf(
                        ProtectedEventResponse.class);

        /*
         * 알 수 없는 mode는 보호 모드로 처리하며,
         * 전체 보호 이벤트 조회가 아니라 하이라이트 조회를 사용한다.
         */
        verify(
                gameEventRepository,
                never())
                .findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                        gameId,
                        GameEvent.SPOILER_PROTECTED_SAFE);
    }

    @Test
    void missingGame_shouldReturnNotFound() {
        // given
        long gameId = 999L;

        when(gameRepository.existsById(gameId))
                .thenReturn(false);

        // when, then
        assertThatThrownBy(
                () ->
                        service.getEvents(
                                gameId,
                                "REVEALED"))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(
                                        exception.getStatusCode())
                                        .isEqualTo(
                                                HttpStatus.NOT_FOUND));

        /*
         * 존재하지 않는 경기는 404를 반환한 뒤 즉시 종료하며,
         * 이벤트 저장소를 조회하지 않는다.
         */
        verifyNoInteractions(gameEventRepository);
    }

    /**
     * 테스트에 사용할 경기 이벤트 엔티티를 생성한다.
     *
     * 공개용 필드도 함께 설정해 두고,
     * 보호 응답에서 해당 값이 노출되지 않는지 검증한다.
     */
    private static GameEvent event(
            Long id,
            long gameId,
            String eventType,
            String spoilerLevel,
            Instant observedAt) {

        GameEvent event =
                new GameEvent();

        event.setId(id);
        event.setGameId(gameId);
        event.setEventType(eventType);
        event.setSpoilerLevel(spoilerLevel);
        event.setSourceType(
                GameEvent.SOURCE_TYPE_PLAY);
        event.setSourceRef(id);
        event.setInning(7);
        event.setInningType("Bottom");
        event.setPayload(Map.of());
        event.setCopyProtected(
                "보호 모드 문구");
        event.setCopyRevealed(
                "공개 모드 문구");
        event.setObservedAt(observedAt);

        return event;
    }
}
