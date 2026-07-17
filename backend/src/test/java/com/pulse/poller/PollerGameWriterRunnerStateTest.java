package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.TeamRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PollerGameWriterRunnerStateTest {

    @Test
    void updateRunnerStates_shouldSaveOnlyChangedPlays() {
        Play unchanged = play(10L, true, false, false);
        Play changed = play(11L, false, false, false);
        PlayRepository playRepository = mock(PlayRepository.class);
        PollerRunnerStateMatcher runnerStateMatcher = mock(PollerRunnerStateMatcher.class);
        PollerGameWriter writer = new PollerGameWriter(
                mock(GameRepository.class),
                playRepository,
                mock(TeamRepository.class),
                mock(GameLifecycleStateMachine.class),
                runnerStateMatcher,
                mock(PlayerStubWriter.class)
        );
        when(playRepository.findByGameIdOrderByPlayOrderAsc(100L)).thenReturn(List.of(unchanged, changed));
        when(runnerStateMatcher.match(anyList(), anyList())).thenReturn(new PollerRunnerStateMatcher.MatchResult(
                List.of(
                        new PollerRunnerStateMatcher.RunnerStateUpdate(10L, true, false, false),
                        new PollerRunnerStateMatcher.RunnerStateUpdate(11L, false, true, false)
                ),
                0,
                0
        ));

        writer.updateRunnerStates(100L, List.<BdlPlateAppearance>of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Play>> captor = ArgumentCaptor.forClass(List.class);
        verify(playRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(changed);
        assertThat(changed.getRunnerOnSecond()).isTrue();
    }

    private static Play play(Long order, boolean first, boolean second, boolean third) {
        Play play = new Play();
        play.setGameId(100L);
        play.setPlayOrder(order);
        play.setRunnerOnFirst(first);
        play.setRunnerOnSecond(second);
        play.setRunnerOnThird(third);
        return play;
    }
}
