package com.pulse.gameprocessing.highlight;

import com.pulse.gameprocessing.aicopy.AiGenerationTrigger;
import com.pulse.scoring.TestScoringProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.message.ScoreTask.PitchSnapshot;
import com.pulse.common.message.ScoreTask.PlateAppearanceSnapshot;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.Lineup;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GameEventExtractorTest {

    private final GameEventRepository gameEventRepository = mock(GameEventRepository.class);
    private final LineupRepository lineupRepository = mock(LineupRepository.class);
    private final PlayRepository playRepository = mock(PlayRepository.class);
    private final AiGenerationTrigger aiGenerationTrigger = mock(AiGenerationTrigger.class);
    private final GameEventExtractor extractor = new GameEventExtractor(
            gameEventRepository,
            lineupRepository,
            playRepository,
            aiGenerationTrigger,
            new AfterCommitExecutor(),
            TestScoringProperties.version5()
    );
    private final AtomicLong eventIds = new AtomicLong();
    private final Set<String> savedEventKeys = new HashSet<>();
    private final Instant observedAt = Instant.parse("2026-07-10T01:00:00Z");

    @BeforeEach
    void setUp() {
        savedEventKeys.clear();
        when(gameEventRepository.existsByGameIdAndEventTypeAndSourceTypeAndSourceRef(
                anyLong(), anyString(), anyString(), anyLong())).thenAnswer(invocation ->
                savedEventKeys.contains(invocation.getArgument(1) + ":" + invocation.getArgument(3)));
        when(gameEventRepository.countByGameIdAndEventType(anyLong(), anyString())).thenReturn(0L);
        when(gameEventRepository.save(any(GameEvent.class))).thenAnswer(invocation -> {
            GameEvent event = invocation.getArgument(0);
            event.setId(eventIds.incrementAndGet());
            savedEventKeys.add(event.getEventType() + ":" + event.getSourceRef());
            return event;
        });
        when(lineupRepository.findByGameIdAndIsProbablePitcherTrue(anyLong())).thenReturn(List.of());
        when(playRepository.countByGameIdAndInningAndInningTypeAndScoringPlayTrue(
                anyLong(), any(), anyString())).thenReturn(2L);
        when(playRepository.findFirstByGameIdAndInningAndInningTypeAndScoringPlayTrueOrderByPlayOrderAsc(
                anyLong(), any(), anyString())).thenAnswer(invocation -> {
                    Play first = play(1L, 0, 0, 0, 0, 1);
                    first.setInning(invocation.getArgument(1));
                    first.setInningType(invocation.getArgument(2));
                    first.setScoringPlay(true);
                    return Optional.of(first);
                });
    }

    @Test
    void extract_shouldPersistProtectedPlayEventsWithoutDuplicatePressureLabels() {
        Play play = play(10L, 3, 2, 2, 1, 1);
        play.setRunnerOnFirst(true);
        play.setRunnerOnSecond(true);
        play.setRunnerOnThird(true);

        extractor.extract(100L, List.of(play), List.of(), 0, observedAt);

        assertThat(savedEvents()).extracting(GameEvent::getEventType)
                .containsExactlyInAnyOrder(
                        GameEventExtractor.EVENT_PRESSURE_BASES_LOADED,
                        GameEventExtractor.EVENT_FULL_COUNT_TWO_OUT
                );
        assertThat(savedEvents()).allMatch(event ->
                GameEvent.SPOILER_PROTECTED_SAFE.equals(event.getSpoilerLevel()));
        verify(aiGenerationTrigger, times(2)).onGameEventPersisted(
                org.mockito.ArgumentMatchers.eq(100L),
                anyLong(),
                org.mockito.ArgumentMatchers.eq(AiGenerationTrigger.MODE_PROTECTED),
                org.mockito.ArgumentMatchers.eq(observedAt));
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

        extractor.extract(100L, List.of(), List.of(plateAppearance), 0, observedAt);

        assertThat(savedEvents()).extracting(GameEvent::getEventType)
                .containsExactlyInAnyOrder(
                        GameEventExtractor.EVENT_LONG_AT_BAT,
                        GameEventExtractor.EVENT_PITCHER_INSTABILITY,
                        GameEventExtractor.EVENT_HARD_CONTACT
                );
        assertThat(savedEvents()).allMatch(event -> GameEvent.SOURCE_TYPE_PA.equals(event.getSourceType()));
    }

    @Test
    @DisplayName("강한 타구 payload에 타구질과 함께 아웃카운트·주자 상황을 담는다")
    void extract_shouldIncludeBaseOutStateInHardContactPayload() {
        PlateAppearanceSnapshot plateAppearance = new PlateAppearanceSnapshot(
                7L,
                6,
                "top",
                10L,
                99L,
                2,
                true,
                false,
                true,
                List.of(new PitchSnapshot(1, 20, 92.0, 101.2, true))
        );

        extractor.extract(100L, List.of(), List.of(plateAppearance), 0, observedAt);

        GameEvent hardContact = savedEvents().stream()
                .filter(event -> GameEventExtractor.EVENT_HARD_CONTACT.equals(event.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(hardContact.getPayload())
                .containsEntry("isBarrel", true)
                .containsEntry("exitVelocity", 101.2)
                .containsEntry("outs", 2)
                .containsEntry("runnerOnFirst", true)
                .containsEntry("runnerOnSecond", false)
                .containsEntry("runnerOnThird", true);
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

        extractor.extract(100L, List.of(first, second), List.of(), 0, observedAt);

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
        verify(aiGenerationTrigger, never()).onGameEventPersisted(
                anyLong(), anyLong(), anyString(), org.mockito.ArgumentMatchers.any(Instant.class));
    }

    @Test
    @DisplayName("윈도가 이동해도 빅이닝 이벤트는 첫 득점 play를 고정 source로 사용한다")
    void bigInningUsesFixedFirstScoringPlaySource() {
        Play firstWindowScore = play(2L, 0, 0, 0, 0, 2);
        firstWindowScore.setScoringPlay(true);
        Play secondWindowScore = play(3L, 0, 0, 0, 0, 3);
        secondWindowScore.setScoringPlay(true);

        extractor.extract(100L, List.of(firstWindowScore), List.of(), 0, observedAt);
        extractor.extract(100L, List.of(secondWindowScore), List.of(), 0, observedAt.plusSeconds(10));

        assertThat(savedEvents()).filteredOn(event -> GameEventExtractor.EVENT_BIG_INNING.equals(event.getEventType()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getSourceRef()).isEqualTo(1L);
                    assertThat(event.getPayload()).containsEntry("scoringPlays", 2L);
                });
    }

    @Test
    @DisplayName("윈도 직전 리더 시드로 경계의 역전 이벤트를 추출한다")
    void leadChangeUsesSeedLeaderAtWindowBoundary() {
        Play changedLeader = play(5L, 0, 0, 0, 2, 3);

        extractor.extract(100L, List.of(changedLeader), List.of(), 1, observedAt);

        assertThat(savedEvents()).extracting(GameEvent::getEventType)
                .contains(GameEventExtractor.EVENT_LEAD_CHANGE);
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
