package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pulse.common.message.ScoreTask.PitchSnapshot;
import com.pulse.common.message.ScoreTask.PlateAppearanceSnapshot;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.Lineup;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.Play;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GameEventExtractorTest {

    private final GameEventRepository gameEventRepository = mock(GameEventRepository.class);
    private final LineupRepository lineupRepository = mock(LineupRepository.class);
    private final AiGenerationTrigger aiGenerationTrigger = mock(AiGenerationTrigger.class);
    private final GameEventExtractor extractor = new GameEventExtractor(
            gameEventRepository,
            lineupRepository,
            aiGenerationTrigger,
            new AfterCommitExecutor(),
            TestScoringProperties.version4()
    );
    private final AtomicLong eventIds = new AtomicLong();
    private final Instant observedAt = Instant.parse("2026-07-10T01:00:00Z");

    @BeforeEach
    void setUp() {
        when(gameEventRepository.existsByGameIdAndEventTypeAndSourceTypeAndSourceRef(
                anyLong(), anyString(), anyString(), anyLong())).thenReturn(false);
        when(gameEventRepository.countByGameIdAndEventType(anyLong(), anyString())).thenReturn(0L);
        when(gameEventRepository.save(any(GameEvent.class))).thenAnswer(invocation -> {
            GameEvent event = invocation.getArgument(0);
            event.setId(eventIds.incrementAndGet());
            return event;
        });
        when(lineupRepository.findByGameIdAndIsProbablePitcherTrue(anyLong())).thenReturn(List.of());
    }

    @Test
    void extract_shouldPersistProtectedPlayEventsWithoutDuplicatePressureLabels() {
        Play play = play(10L, 3, 2, 2, 1, 1);
        play.setRunnerOnFirst(true);
        play.setRunnerOnSecond(true);
        play.setRunnerOnThird(true);

        extractor.extract(100L, List.of(play), List.of(), observedAt);

        assertThat(savedEvents()).extracting(GameEvent::getEventType)
                .containsExactlyInAnyOrder(
                        GameEventExtractor.EVENT_PRESSURE_BASES_LOADED,
                        GameEventExtractor.EVENT_FULL_COUNT_TWO_OUT
                );
        assertThat(savedEvents()).allMatch(event ->
                GameEvent.SPOILER_PROTECTED_SAFE.equals(event.getSpoilerLevel()));
    }

    @Test
    void extract_shouldPersistAllPlateAppearanceEventKinds() {
        Lineup starter = new Lineup();
        starter.setPlayerId(99L);
        when(lineupRepository.findByGameIdAndIsProbablePitcherTrue(100L)).thenReturn(List.of(starter));

        List<PitchSnapshot> pitches = new ArrayList<>();
        for (int number = 1; number <= 10; number++) {
            pitches.add(new PitchSnapshot(number, 89 + number, 95.0, null, false));
        }
        pitches.add(new PitchSnapshot(11, 100, 92.0, 101.2, true));
        PlateAppearanceSnapshot plateAppearance = new PlateAppearanceSnapshot(
                7L,
                6,
                "top",
                10L,
                99L,
                1,
                false,
                false,
                false,
                pitches
        );

        extractor.extract(100L, List.of(), List.of(plateAppearance), observedAt);

        assertThat(savedEvents()).extracting(GameEvent::getEventType)
                .containsExactlyInAnyOrder(
                        GameEventExtractor.EVENT_LONG_AT_BAT,
                        GameEventExtractor.EVENT_PITCHER_INSTABILITY,
                        GameEventExtractor.EVENT_HARD_CONTACT
                );
        assertThat(savedEvents()).allMatch(event -> GameEvent.SOURCE_TYPE_PA.equals(event.getSourceType()));
    }

    @Test
    void extract_shouldPersistAllRevealedPlayEventKinds() {
        Play first = play(1L, 0, 0, 0, 1, 0);
        first.setScoringPlay(true);
        first.setScoreValue(1);
        first.setText("RBI single");

        Play second = play(2L, 0, 0, 0, 1, 2);
        second.setScoringPlay(true);
        second.setScoreValue(2);
        second.setText("Batter homered to right field");

        extractor.extract(100L, List.of(first, second), List.of(), observedAt);

        assertThat(savedEvents()).extracting(GameEvent::getEventType)
                .containsExactlyInAnyOrder(
                        GameEventExtractor.EVENT_SCORING_PLAY,
                        GameEventExtractor.EVENT_SCORING_PLAY,
                        GameEventExtractor.EVENT_LEAD_CHANGE,
                        GameEventExtractor.EVENT_HOME_RUN,
                        GameEventExtractor.EVENT_BIG_INNING
                );
        assertThat(savedEvents()).allMatch(event ->
                GameEvent.SPOILER_REVEALED_ONLY.equals(event.getSpoilerLevel()));
    }

    private List<GameEvent> savedEvents() {
        ArgumentCaptor<GameEvent> captor = ArgumentCaptor.forClass(GameEvent.class);
        org.mockito.Mockito.verify(gameEventRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private static Play play(
            long order,
            int balls,
            int strikes,
            int outs,
            int homeScore,
            int awayScore
    ) {
        Play play = new Play();
        play.setGameId(100L);
        play.setPlayOrder(order);
        play.setInning(6);
        play.setInningType("Top");
        play.setBatterId(10L);
        play.setPitcherId(99L);
        play.setBalls(balls);
        play.setStrikes(strikes);
        play.setOuts(outs);
        play.setHomeScore(homeScore);
        play.setAwayScore(awayScore);
        return play;
    }
}
