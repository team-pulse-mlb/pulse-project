package com.pulse.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.FinalHeadlineContext;
import com.pulse.common.ai.RevealedEventCopyContext;
import com.pulse.domain.Game;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.GameRepository;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.WatchScoreRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiCopyContextServiceTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GameEventRepository eventRepository = mock(GameEventRepository.class);
    private final WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
    private final PlayerRepository playerRepository = mock(PlayerRepository.class);
    private final AiCopyContextService service = new AiCopyContextService(
            gameRepository, eventRepository, watchScoreRepository, playerRepository);

    @BeforeEach
    void 기본값을_설정한다() {
        when(eventRepository.findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                1L, GameEvent.SPOILER_PROTECTED_SAFE)).thenReturn(List.of());
        when(watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(1L)).thenReturn(Optional.empty());
    }

    @Test
    void keyMoments는_중복과_쿼터를_적용하고_최신_우선_선별_후_시간순으로_정렬한다() {
        List<GameEvent> events = new ArrayList<>();
        events.add(event(1, 1, "pressure_bases_loaded", 1));
        events.add(event(2, 1, "pressure_bases_loaded", 2));
        events.add(event(3, 1, "hard_contact", 3));
        events.add(event(4, 1, "long_at_bat", 4));
        events.add(event(5, 2, "pressure_bases_loaded", 5));
        events.add(event(6, 3, "pressure_bases_loaded", 6));
        events.add(event(7, 4, "hard_contact", 7));
        events.add(event(8, 5, "long_at_bat", 8));
        events.add(event(9, 6, "full_count_two_out", 9));
        events.add(event(10, 7, "pitcher_instability", 10));
        events.add(event(11, 8, "pressure_scoring_position", 11));
        events.add(event(12, 9, "unknown", 12));

        List<FinalHeadlineContext.KeyMoment> result = AiCopyContextService.selectKeyMoments(events);

        assertThat(result).hasSize(8);
        assertThat(result).doesNotContain(new FinalHeadlineContext.KeyMoment(1, "만루 승부"));
        assertThat(result).extracting(FinalHeadlineContext.KeyMoment::inning)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(result).filteredOn(moment -> moment.label().equals("만루 승부")).hasSize(2);
    }

    @Test
    void evidence는_유형별_사실_수치만_남긴다() {
        assertThat(AiCopyContextService.projectEvidence("pressure_bases_loaded", Map.of(
                "outs", 2, "balls", 3, "strikes", 2, "runnerOnFirst", true, "text", "득점")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("outs", 2, "balls", 3, "strikes", 2));
        assertThat(AiCopyContextService.projectEvidence("pitcher_instability", Map.of(
                "pitcherPitchCount", 95, "velocityDropMph", 2.1, "result", "교체")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("pitcherPitchCount", 95, "velocityDropMph", 2.1));
        assertThat(AiCopyContextService.projectEvidence("lead_change", Map.of("scoreValue", 1))).isEmpty();
    }

    @Test
    void 이벤트_컨텍스트의_차단_조건을_적용한다() {
        when(eventRepository.findById(10L)).thenReturn(Optional.empty());
        assertThat(service.eventCopyContext(1L, 10L, AiCopyMode.PROTECTED)).isEmpty();

        GameEvent mismatch = event(10, 1, "hard_contact", 1);
        mismatch.setGameId(2L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(mismatch));
        assertThat(service.eventCopyContext(1L, 10L, AiCopyMode.PROTECTED)).isEmpty();

        GameEvent unknownLevel = event(11, 1, "hard_contact", 1);
        unknownLevel.setSpoilerLevel("UNKNOWN");
        when(eventRepository.findById(11L)).thenReturn(Optional.of(unknownLevel));
        assertThat(service.eventCopyContext(1L, 11L, AiCopyMode.REVEALED)).isEmpty();

        GameEvent revealedOnly = event(12, 1, "home_run", 1);
        revealedOnly.setSpoilerLevel(GameEvent.SPOILER_REVEALED_ONLY);
        when(eventRepository.findById(12L)).thenReturn(Optional.of(revealedOnly));
        assertThat(service.eventCopyContext(1L, 12L, AiCopyMode.PROTECTED)).isEmpty();

        GameEvent unknownType = event(13, 1, "unknown", 1);
        when(eventRepository.findById(13L)).thenReturn(Optional.of(unknownType));
        assertThat(service.eventCopyContext(1L, 13L, AiCopyMode.REVEALED)).isEmpty();
    }

    @Test
    void 공개_이벤트만_추가_필드와_화이트리스트_evidence를_포함한다() {
        GameEvent event = event(20, 8, "home_run", 20);
        event.setSpoilerLevel(GameEvent.SPOILER_REVEALED_ONLY);
        event.setInningType("TOP");
        event.setPayload(Map.of("scoreValue", 2, "text", "홈런"));
        when(eventRepository.findById(20L)).thenReturn(Optional.of(event));

        RevealedEventCopyContext context = (RevealedEventCopyContext) service
                .eventCopyContext(1L, 20L, AiCopyMode.REVEALED).orElseThrow();

        assertThat(context.inningType()).isEqualTo("TOP");
        assertThat(context.batter()).isNull();
        assertThat(context.pitcher()).isNull();
        assertThat(context.evidence()).containsOnlyKeys("scoreValue");
    }

    @Test
    void 경기_없음과_미종료_경기는_헤드라인_컨텍스트를_반환하지_않는다() {
        when(gameRepository.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.finalHeadlineContext(1L, AiCopyMode.PROTECTED)).isEmpty();

        Game live = game(Game.STATUS_IN_PROGRESS, 2, 1);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(live));
        assertThat(service.finalHeadlineContext(1L, AiCopyMode.PROTECTED)).isEmpty();
    }

    @Test
    void 최종_점수와_승자는_공개_헤드라인에만_포함한다() {
        Game game = game(Game.STATUS_FINAL, 5, 3);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        FinalHeadlineContext protectedContext = service
                .finalHeadlineContext(1L, AiCopyMode.PROTECTED).orElseThrow();
        FinalHeadlineContext revealedContext = service
                .finalHeadlineContext(1L, AiCopyMode.REVEALED).orElseThrow();

        assertThat(protectedContext.finalScore()).isNull();
        assertThat(protectedContext.winner()).isNull();
        assertThat(revealedContext.finalScore()).isEqualTo(new FinalHeadlineContext.FinalScore(5, 3));
        assertThat(revealedContext.winner()).isEqualTo("home");
        assertThat(revealedContext.contextHash()).isNotEqualTo(protectedContext.contextHash());
    }

    private static GameEvent event(long id, int inning, String eventType, long second) {
        GameEvent event = new GameEvent();
        event.setId(id);
        event.setGameId(1L);
        event.setInning(inning);
        event.setEventType(eventType);
        event.setSpoilerLevel(GameEvent.SPOILER_PROTECTED_SAFE);
        event.setObservedAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(second));
        return event;
    }

    private static Game game(String status, int homeRuns, int awayRuns) {
        Game game = new Game();
        game.setId(1L);
        game.setStatus(status);
        game.setHomeRuns(homeRuns);
        game.setAwayRuns(awayRuns);
        return game;
    }
}
