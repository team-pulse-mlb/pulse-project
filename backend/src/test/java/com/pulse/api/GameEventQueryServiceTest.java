package com.pulse.api;

import com.pulse.api.GameEventQueryService.GameEventsResponse;
import com.pulse.api.GameEventQueryService.ProtectedEventResponse;
import com.pulse.api.GameEventQueryService.RevealedEventResponse;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private final PlayerRepository playerRepository =
            mock(PlayerRepository.class);

    private final GameEventQueryService service =
            new GameEventQueryService(
                    gameRepository,
                    gameEventRepository,
                    playerRepository);

    @Test
    void protectedMode_shouldReturnOnlyProtectedSafeEvents() {
        // given
        long gameId = 100L;

        GameEvent protectedEvent =
                event(
                        1L,
                        gameId,
                        "pressure_bases_loaded",
                        GameEvent.SPOILER_PROTECTED_SAFE,
                        Instant.parse("2026-07-14T10:00:00Z"));

        /*
         * 원본 엔티티에는 공개 모드용 정보가 들어 있어도
         * 보호 응답 DTO에는 포함되지 않아야 한다.
         */
        protectedEvent.setInningType("Bottom");
        protectedEvent.setBatterId(10L);
        protectedEvent.setPitcherId(20L);
        protectedEvent.setPayload(
                Map.of(
                        "outs", 2,
                        "balls", 3,
                        "strikes", 2));

        GameEvent revealedOnlyEvent =
                event(
                        2L,
                        gameId,
                        "scoring_play",
                        GameEvent.SPOILER_REVEALED_ONLY,
                        Instant.parse("2026-07-14T10:01:00Z"));

        when(gameRepository.existsById(gameId))
                .thenReturn(true);

        when(
                gameEventRepository
                        .findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                                gameId,
                                GameEvent.SPOILER_PROTECTED_SAFE))
                .thenReturn(List.of(protectedEvent));

        /*
         * 보호 모드에서는 REVEALED_ONLY 저장소 조회 자체가
         * 실행되지 않아야 하므로 이 값은 응답에 사용되지 않는다.
         */
        when(
                gameEventRepository
                        .findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                                gameId,
                                GameEvent.SPOILER_REVEALED_ONLY))
                .thenReturn(List.of(revealedOnlyEvent));

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
                                    .isEqualTo("pressure_bases_loaded");
                            assertThat(event.inning())
                                    .isEqualTo(7);
                            assertThat(event.label())
                                    .isEqualTo("만루 승부");
                            assertThat(event.copy())
                                    .isEqualTo("보호 모드 문구");
                        });

        /*
         * 보호 모드는 득점 이벤트와 선수 이름을 조회하지 않는다.
         */
        verify(
                gameEventRepository,
                never())
                .findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                        gameId,
                        GameEvent.SPOILER_REVEALED_ONLY);

        verifyNoInteractions(playerRepository);
    }

    @Test
    void revealedMode_shouldReturnAllAllowedEventsInObservedOrder() {
        // given
        long gameId = 200L;

        /*
         * 선수 ID가 없는 보호 안전 이벤트도 공개 타임라인에 포함된다.
         * null ID 때문에 Map 조회 예외가 발생하지 않아야 한다.
         */
        GameEvent protectedEventWithoutPlayers =
                event(
                        20L,
                        gameId,
                        "pressure_bases_loaded",
                        GameEvent.SPOILER_PROTECTED_SAFE,
                        Instant.parse("2026-07-14T10:02:00Z"));

        protectedEventWithoutPlayers.setInningType("Bottom");
        protectedEventWithoutPlayers.setBatterId(null);
        protectedEventWithoutPlayers.setPitcherId(null);

        GameEvent scoringEvent =
                event(
                        10L,
                        gameId,
                        "scoring_play",
                        GameEvent.SPOILER_REVEALED_ONLY,
                        Instant.parse("2026-07-14T10:01:00Z"));

        scoringEvent.setInning(6);
        scoringEvent.setInningType("Top");
        scoringEvent.setBatterId(101L);
        scoringEvent.setPitcherId(202L);
        scoringEvent.setPayload(Map.of());

        Player batter =
                player(
                        101L,
                        "Test Batter");

        Player pitcher =
                player(
                        202L,
                        "Test Pitcher");

        when(gameRepository.existsById(gameId))
                .thenReturn(true);

        when(
                gameEventRepository
                        .findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                                gameId,
                                GameEvent.SPOILER_PROTECTED_SAFE))
                .thenReturn(
                        List.of(protectedEventWithoutPlayers));

        when(
                gameEventRepository
                        .findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                                gameId,
                                GameEvent.SPOILER_REVEALED_ONLY))
                .thenReturn(
                        List.of(scoringEvent));

        when(playerRepository.findAllById(any()))
                .thenReturn(
                        List.of(
                                batter,
                                pitcher));

        // when
        GameEventsResponse response =
                service.getEvents(
                        gameId,
                        " revealed ");

        // then
        assertThat(response.events())
                .hasSize(2);

        /*
         * 두 저장소의 결과를 합친 뒤 observedAt 기준으로
         * 다시 정렬했는지 확인한다.
         */
        assertThat(response.events().get(0))
                .isInstanceOfSatisfying(
                        RevealedEventResponse.class,
                        event -> {
                            assertThat(event.eventId())
                                    .isEqualTo(10L);
                            assertThat(event.label())
                                    .isEqualTo("득점");
                            assertThat(event.inningType())
                                    .isEqualTo("Top");
                            assertThat(event.copy())
                                    .isEqualTo("공개 모드 문구");
                            assertThat(event.players().batter())
                                    .isEqualTo("Test Batter");
                            assertThat(event.players().pitcher())
                                    .isEqualTo("Test Pitcher");
                            assertThat(event.evidence())
                                    .isEmpty();
                        });

        assertThat(response.events().get(1))
                .isInstanceOfSatisfying(
                        RevealedEventResponse.class,
                        event -> {
                            assertThat(event.eventId())
                                    .isEqualTo(20L);
                            assertThat(event.label())
                                    .isEqualTo("만루 승부");
                            assertThat(event.players().batter())
                                    .isNull();
                            assertThat(event.players().pitcher())
                                    .isNull();
                        });
    }

    @Test
    void invalidMode_shouldFallBackToProtectedMode() {
        // given
        long gameId = 300L;

        GameEvent protectedEvent =
                event(
                        30L,
                        gameId,
                        "long_at_bat",
                        GameEvent.SPOILER_PROTECTED_SAFE,
                        Instant.parse("2026-07-14T11:00:00Z"));

        when(gameRepository.existsById(gameId))
                .thenReturn(true);

        when(
                gameEventRepository
                        .findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                                gameId,
                                GameEvent.SPOILER_PROTECTED_SAFE))
                .thenReturn(List.of(protectedEvent));

        // when
        GameEventsResponse response =
                service.getEvents(
                        gameId,
                        "invalid-mode");

        // then
        assertThat(response.events())
                .singleElement()
                .isInstanceOf(ProtectedEventResponse.class);

        verify(
                gameEventRepository,
                never())
                .findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                        gameId,
                        GameEvent.SPOILER_REVEALED_ONLY);
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
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(
                gameEventRepository,
                playerRepository);
    }

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
        event.setSourceType(GameEvent.SOURCE_TYPE_PLAY);
        event.setSourceRef(id);
        event.setInning(7);
        event.setInningType("Bottom");
        event.setPayload(Map.of());
        event.setCopyProtected("보호 모드 문구");
        event.setCopyRevealed("공개 모드 문구");
        event.setObservedAt(observedAt);

        return event;
    }

    private static Player player(
            Long id,
            String fullName) {

        Player player =
                new Player();

        player.setId(id);
        player.setFullName(fullName);

        return player;
    }
}
