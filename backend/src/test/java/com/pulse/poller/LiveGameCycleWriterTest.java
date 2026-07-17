package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.common.message.ScoreTaskOutboxDispatcher;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.ScoreTaskOutbox;
import com.pulse.domain.ScoreTaskOutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({LiveGameCycleWriter.class, PollerGameWriter.class, ScoreTaskFactory.class, ScoreTaskPublisher.class,
        GameLifecycleStateMachine.class, PollerRunnerStateMatcher.class, PlayerStubWriter.class})
@TestPropertySource(properties = "spring.flyway.enabled=false")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LiveGameCycleWriterTest {

    @Autowired
    private LiveGameCycleWriter cycleWriter;

    @Autowired
    private PollerGameWriter gameWriter;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private PlayRepository playRepository;

    @MockitoBean
    private ScoreTaskOutboxRepository scoreTaskOutboxRepository;

    @MockitoBean
    private ScoreTaskOutboxDispatcher scoreTaskOutboxDispatcher;

    @MockitoBean
    private AfterCommitExecutor afterCommitExecutor;

    private final Instant observedAt = Instant.parse("2026-07-08T00:00:00Z");

    @Test
    void write_shouldRollbackPlayAndCursorWhenOutboxInsertFails() {
        Game game = gameWriter.upsertGame(game(), observedAt).game();
        when(scoreTaskOutboxRepository.findByGameIdAndObservedAt(100L, observedAt))
                .thenReturn(Optional.empty());
        // 경쟁 방지 insert 경로(insertPending)가 outbox 저장 지점이므로 여기서 실패를 일으킨다.
        when(scoreTaskOutboxRepository.insertPending(any(ScoreTaskOutbox.class)))
                .thenThrow(new DataIntegrityViolationException("outbox 저장 실패"));

        assertThatThrownBy(() -> cycleWriter.write(game, List.of(play(10L)), List.of(), observedAt))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("outbox 저장 실패");

        Game savedGame = gameRepository.findById(100L).orElseThrow();
        assertThat(playRepository.findByGameIdOrderByPlayOrderAsc(100L)).isEmpty();
        assertThat(savedGame.getLastPlayOrder()).isNull();
    }

    private static BdlGame game() {
        return new BdlGame(
                100L,
                "2026-07-08T00:00:00Z",
                Game.STATUS_IN_PROGRESS,
                1,
                "Home",
                "Away",
                new BdlGame.Team(1L, "Home", "HOM"),
                new BdlGame.Team(2L, "Away", "AWY"),
                new BdlGame.TeamData(1, List.of(1)),
                new BdlGame.TeamData(0, List.of(0)),
                "Test Park"
        );
    }

    private static BdlPlay play(Long order) {
        return new BdlPlay(
                order,
                "at_bat",
                1,
                "Top",
                "play",
                0,
                0,
                false,
                0,
                1,
                2,
                1,
                7L,
                99L
        );
    }
}
