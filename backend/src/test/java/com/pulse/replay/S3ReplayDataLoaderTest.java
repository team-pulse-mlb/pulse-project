package com.pulse.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import com.pulse.poller.PlayerStubWriter;
import com.pulse.replay.S3RawArchiveClient.RawEnvelope;
import com.pulse.gameprocessing.application.ScoreRecalculationService;
import java.time.Instant;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.ApplicationArguments;

class S3ReplayDataLoaderTest {

    private static final long GAME_ID = 5059180L;
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-11T01:17:00Z");

    private final S3RawArchiveClient archiveClient = mock(S3RawArchiveClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final PlayRepository playRepository = mock(PlayRepository.class);
    private final PlayerStubWriter playerStubWriter = mock(PlayerStubWriter.class);
    private final ScoreRecalculationService scoreRecalculationService = mock(ScoreRecalculationService.class);

    @Test
    void 백필Play는선수Fk를보장한뒤출처와선수Id를저장한다() throws Exception {
        RawEnvelope envelope = playsEnvelope(true);
        stream(envelope);
        when(playRepository.existsByGameIdAndPlayOrder(GAME_ID, 123L)).thenReturn(false);

        loader().run(mock(ApplicationArguments.class));

        InOrder order = inOrder(playerStubWriter, playRepository);
        order.verify(playerStubWriter).ensurePlayerExists(701L, OBSERVED_AT);
        order.verify(playerStubWriter).ensurePlayerExists(801L, OBSERVED_AT);
        ArgumentCaptor<Play> captor = ArgumentCaptor.forClass(Play.class);
        order.verify(playRepository).save(captor.capture());
        assertThat(captor.getValue().getBatterId()).isEqualTo(701L);
        assertThat(captor.getValue().getPitcherId()).isEqualTo(801L);
        assertThat(captor.getValue().getBackfilled()).isTrue();
        assertThat(captor.getValue().getSource()).isEqualTo("S3_BACKFILL");
        verify(scoreRecalculationService).recalculate(GAME_ID, OBSERVED_AT);
    }

    @Test
    void 라이브아카이브Play는라이브출처로저장한다() throws Exception {
        stream(playsEnvelope(false));
        when(playRepository.existsByGameIdAndPlayOrder(GAME_ID, 123L)).thenReturn(false);

        loader().run(mock(ApplicationArguments.class));

        ArgumentCaptor<Play> captor = ArgumentCaptor.forClass(Play.class);
        verify(playRepository).save(captor.capture());
        assertThat(captor.getValue().getBackfilled()).isFalse();
        assertThat(captor.getValue().getSource()).isEqualTo("S3_LIVE_ARCHIVE");
    }

    private S3ReplayDataLoader loader() {
        return new S3ReplayDataLoader(
                archiveClient,
                new ReplayProperties(null, GAME_ID, null, 200),
                objectMapper,
                gameRepository,
                playRepository,
                playerStubWriter,
                scoreRecalculationService);
    }

    @SuppressWarnings("unchecked")
    private void stream(RawEnvelope envelope) {
        doAnswer(invocation -> {
            Consumer<RawEnvelope> consumer = invocation.getArgument(0);
            consumer.accept(envelope);
            return 1;
        }).when(archiveClient).streamReplayObjects(org.mockito.ArgumentMatchers.any());
    }

    private RawEnvelope playsEnvelope(boolean backfilled) throws Exception {
        return new RawEnvelope(
                "raw/plays/game_id=" + GAME_ID + "/object.json.gz",
                OBSERVED_AT,
                "/plays",
                objectMapper.readTree("{\"game_id\":" + GAME_ID + "}"),
                objectMapper.readTree("""
                        {"data":[{
                          "order":123,
                          "type":"Play Result",
                          "inning":7,
                          "inning_type":"Top",
                          "home_score":3,
                          "away_score":2,
                          "batter_id":701,
                          "pitcher_id":801
                        }]}
                        """),
                backfilled);
    }
}
