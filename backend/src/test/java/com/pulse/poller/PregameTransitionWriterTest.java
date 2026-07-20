package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.client.BdlDtos.BdlLineup;
import com.pulse.common.client.BdlDtos.BdlPlayerSeasonStat;
import com.pulse.common.message.ScoreTaskFactory;
import com.pulse.common.message.ScoreTaskOutboxDispatcher;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.Game;
import com.pulse.domain.GameLifecycle;
import com.pulse.domain.GameRepository;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.PlayerSeasonStatId;
import com.pulse.domain.PlayerSeasonStatRepository;
import com.pulse.domain.ScoreTaskOutbox;
import com.pulse.domain.ScoreTaskOutboxInsertRepository;
import com.pulse.domain.ScoreTaskOutboxRepository;
import com.pulse.poller.PregameTransitionWriter.PregameWriteRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({PregameTransitionWriter.class, PregameGameWriter.class, PlayerStubWriter.class,
        ScoreTaskFactory.class, ScoreTaskPublisher.class, AfterCommitExecutor.class})
@TestPropertySource(properties = "spring.flyway.enabled=false")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PregameTransitionWriterTest {

    @Autowired
    private PregameTransitionWriter writer;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private LineupRepository lineupRepository;

    @Autowired
    private PlayerSeasonStatRepository playerSeasonStatRepository;

    @MockitoBean
    private ScoreTaskOutboxRepository scoreTaskOutboxRepository;

    @MockitoBean
    private ScoreTaskOutboxInsertRepository scoreTaskOutboxInsertRepository;

    @MockitoBean
    private ScoreTaskOutboxDispatcher scoreTaskOutboxDispatcher;

    private final Instant observedAt = Instant.parse("2026-07-08T00:00:00Z");

    @Test
    void write_shouldRollbackPregameInputsWhenOutboxInsertFails() {
        Game game = gameRepository.saveAndFlush(game(100L));
        when(scoreTaskOutboxRepository.findByGameIdAndObservedAt(100L, observedAt))
                .thenReturn(Optional.empty());
        when(scoreTaskOutboxInsertRepository.insertPending(any(ScoreTaskOutbox.class)))
                .thenThrow(new DataIntegrityViolationException("outbox 저장 실패"));

        PregameWriteRequest request = request(game, 1L, 7L);

        assertThatThrownBy(() -> writer.write(request))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("outbox 저장 실패");

        assertThat(lineupRepository.findByGameIdAndPlayerId(100L, 7L)).isEmpty();
        assertThat(playerSeasonStatRepository.findById(new PlayerSeasonStatId(2026, 7L))).isEmpty();
        verify(scoreTaskOutboxDispatcher, never()).publishTask(any());
    }

    @Test
    void write_shouldCommitInputsAndDispatchTaskAfterCommit() {
        Game game = gameRepository.saveAndFlush(game(101L));
        when(scoreTaskOutboxRepository.findByGameIdAndObservedAt(101L, observedAt))
                .thenReturn(Optional.empty());
        when(scoreTaskOutboxInsertRepository.insertPending(any(ScoreTaskOutbox.class))).thenReturn(true);

        writer.write(request(game, 2L, 8L));

        assertThat(lineupRepository.findByGameIdAndPlayerId(101L, 8L)).isPresent();
        assertThat(playerSeasonStatRepository.findById(new PlayerSeasonStatId(2026, 8L))).isPresent();
        ArgumentCaptor<ScoreTaskOutbox> outboxCaptor = ArgumentCaptor.forClass(ScoreTaskOutbox.class);
        verify(scoreTaskOutboxInsertRepository).insertPending(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getObservedAt()).isEqualTo(observedAt);
        verify(scoreTaskOutboxDispatcher).publishTask(outboxCaptor.getValue().getOutboxId());
    }

    private PregameWriteRequest request(Game game, long lineupId, long playerId) {
        return new PregameWriteRequest(
                List.of(lineup(lineupId, game.getId(), playerId)),
                List.of(),
                Map.of(),
                null,
                2026,
                List.of(seasonStat(playerId)),
                Map.of(game.getId(), Set.of(playerId)),
                Set.of(),
                Map.of(game.getId(), game),
                observedAt
        );
    }

    private static Game game(long id) {
        Game game = new Game();
        game.setId(id);
        game.setStatus(Game.STATUS_SCHEDULED);
        game.setLifecycleState(GameLifecycle.PREGAME_NEAR.name());
        return game;
    }

    private static BdlLineup lineup(long id, long gameId, long playerId) {
        return new BdlLineup(
                id,
                gameId,
                new BdlLineup.Player(playerId, "Pitcher Seven", "Pitcher", "Seven", "SP"),
                new BdlLineup.TeamRef(1L),
                null,
                "SP",
                true
        );
    }

    private static BdlPlayerSeasonStat seasonStat(long playerId) {
        return new BdlPlayerSeasonStat(
                playerId,
                null,
                2026,
                new BigDecimal("3.10"),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
