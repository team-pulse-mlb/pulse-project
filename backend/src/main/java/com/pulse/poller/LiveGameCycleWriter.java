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
        return write(game, plays, plateAppearances, observedAt, game.getLifecycleState());
    }

    /** 새 play가 없는 동안에도 DB 최신 play 기준 감쇠 재계산 task를 발행한다. */
    @Transactional
    public boolean publishHeartbeat(Game game, Instant observedAt) {
        Play latestPlay = findLatestPlay(game.getId());
        if (latestPlay == null) {
            return false;
        }
        scoreTaskPublisher.publish(scoreTaskFactory.liveTask(game, latestPlay, observedAt));
        return true;
    }

    /** 종료 전환 저장 뒤 drain하더라도 마지막 play task는 라이브 점수 계산 경로로 보낸다. */
    @Transactional
    public CycleWriteResult writeTerminalDrain(
            Game game,
            List<BdlPlay> plays,
            List<BdlPlateAppearance> plateAppearances,
            Instant observedAt
    ) {
        return write(game, plays, plateAppearances, observedAt, GameLifecycle.LIVE.name());
    }

    private CycleWriteResult write(
            Game game,
            List<BdlPlay> plays,
            List<BdlPlateAppearance> plateAppearances,
            Instant observedAt,
            String taskLifecycleState
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
        Play latestPlay = findLatestPlay(game.getId());
        if (latestPlay == null) {
            throw new IllegalStateException("저장된 최신 플레이를 찾을 수 없습니다.");
        }
        scoreTaskPublisher.publish(scoreTaskFactory.liveTask(
                game,
                latestPlay,
                observedAt,
                plateAppearances,
                taskLifecycleState
        ));
        return new CycleWriteResult(inserted, runnerStateResult);
    }

    private Play findLatestPlay(long gameId) {
        return playRepository.findByGameIdOrderByPlayOrderDesc(gameId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);
    }

    public record CycleWriteResult(
            int inserted,
            PollerRunnerStateMatcher.MatchResult runnerStateResult
    ) {
    }
}
