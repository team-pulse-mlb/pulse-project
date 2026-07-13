package com.pulse.poller;

import com.pulse.common.client.BdlDtos.BdlPitch;
import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.message.ScoreTask;
import com.pulse.common.message.ScoreTask.PitchSnapshot;
import com.pulse.common.message.ScoreTask.PlateAppearanceSnapshot;
import com.pulse.domain.Game;
import com.pulse.domain.Play;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScoreTaskFactory {

    /** 경기 전 계산 task의 lifecycleState 값. GameLifecycle 상태가 아니라 계산 분기 표식이다. */
    public static final String PREGAME_LIFECYCLE = "PREGAME";

    public ScoreTask pregameTask(Game game, Instant observedAt) {
        return new ScoreTask(
                game.getId(),
                observedAt,
                null,
                PREGAME_LIFECYCLE,
                null
        );
    }

    public ScoreTask liveTask(Game game, Play latestPlay, Instant observedAt) {
        return liveTask(game, latestPlay, observedAt, List.of());
    }

    public ScoreTask liveTask(
            Game game,
            Play latestPlay,
            Instant observedAt,
            List<BdlPlateAppearance> plateAppearances
    ) {
        return new ScoreTask(
                game.getId(),
                observedAt,
                latestPlay == null ? game.getLastPlayOrder() : latestPlay.getPlayOrder(),
                game.getLifecycleState(),
                latestPlay == null ? null : situation(latestPlay),
                plateAppearances == null ? List.of() : plateAppearances.stream()
                        .filter(plateAppearance -> plateAppearance.paNumber() != null)
                        .sorted(Comparator.comparing(BdlPlateAppearance::paNumber))
                        .map(ScoreTaskFactory::plateAppearanceSnapshot)
                        .toList()
        );
    }

    public ScoreTask terminalTask(Game game, Instant observedAt) {
        return new ScoreTask(
                game.getId(),
                observedAt,
                game.getLastPlayOrder(),
                game.getLifecycleState(),
                null
        );
    }

    private static ScoreTask.Situation situation(Play play) {
        return ScoreTask.Situation.of(
                play.getOuts(),
                play.getBalls(),
                play.getStrikes(),
                play.getRunnerOnFirst(),
                play.getRunnerOnSecond(),
                play.getRunnerOnThird()
        );
    }

    private static PlateAppearanceSnapshot plateAppearanceSnapshot(BdlPlateAppearance plateAppearance) {
        return new PlateAppearanceSnapshot(
                plateAppearance.paNumber(),
                plateAppearance.inning(),
                plateAppearance.halfInning(),
                plateAppearance.batterId(),
                plateAppearance.pitcherId(),
                plateAppearance.outs(),
                Boolean.TRUE.equals(plateAppearance.runnerOnFirst()),
                Boolean.TRUE.equals(plateAppearance.runnerOnSecond()),
                Boolean.TRUE.equals(plateAppearance.runnerOnThird()),
                plateAppearance.pitches().stream()
                        .sorted(Comparator.comparing(
                                BdlPitch::pitchNumber,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                        .map(pitch -> new PitchSnapshot(
                                pitch.pitchNumber(),
                                pitch.pitcherPitchCount(),
                                pitch.releaseSpeed(),
                                pitch.exitVelocity(),
                                Boolean.TRUE.equals(pitch.isBarrel())
                        ))
                        .toList()
        );
    }
}
