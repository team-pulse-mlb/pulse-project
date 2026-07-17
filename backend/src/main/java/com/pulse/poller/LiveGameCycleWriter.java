package com.pulse.poller;

import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.domain.Game;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LiveGameCycleWriter {

    private final PollerGameWriter gameWriter;
    private final PlayRepository playRepository;
    private final ScoreTaskFactory scoreTaskFactory;
    private final ScoreTaskPublisher scoreTaskPublisher;

    @Transactional
    public CycleWriteResult write(
            Game game,
            List<BdlPlay> plays,
            List<BdlPlateAppearance> plateAppearances,
            Instant observedAt
    ) {
        int inserted = 0;
        for (BdlPlay play : plays) {
            if (gameWriter.appendPlay(game, play, observedAt)) {
                inserted++;
            }
        }
        if (inserted == 0) {
            return new CycleWriteResult(0, new PollerRunnerStateMatcher.MatchResult(List.of(), 0, 0));
        }

        PollerRunnerStateMatcher.MatchResult runnerStateResult =
                gameWriter.updateRunnerStates(game.getId(), plateAppearances);
        Play latestPlay = playRepository.findByGameIdOrderByPlayOrderDesc(
                        game.getId(),
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("저장된 최신 플레이를 찾을 수 없습니다."));
        scoreTaskPublisher.publish(scoreTaskFactory.liveTask(game, latestPlay, observedAt, plateAppearances));
        return new CycleWriteResult(inserted, runnerStateResult);
    }

    public record CycleWriteResult(
            int inserted,
            PollerRunnerStateMatcher.MatchResult runnerStateResult
    ) {
    }
}
