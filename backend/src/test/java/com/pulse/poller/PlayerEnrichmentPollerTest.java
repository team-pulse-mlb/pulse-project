package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.client.BalldontlieClient;
import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class PlayerEnrichmentPollerTest {

    private final BalldontlieClient balldontlieClient = mock(BalldontlieClient.class);
    private final PlayerRepository playerRepository = mock(PlayerRepository.class);
    private final PlayerEnrichmentWriter playerEnrichmentWriter = mock(PlayerEnrichmentWriter.class);
    private final Instant now = Instant.parse("2026-07-11T00:00:00Z");
    private final PlayerEnrichmentPoller poller = new PlayerEnrichmentPoller(
            balldontlieClient,
            playerRepository,
            playerEnrichmentWriter,
            properties(),
            new PollerRateLimiter(1000, Clock.fixed(now, ZoneOffset.UTC)),
            Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void poll_shouldNotCallApiWhenNoStubPlayerRemains() {
        when(playerRepository.findByFullNameIsNull(PageRequest.of(0, 300))).thenReturn(List.of());

        poller.poll();

        verify(balldontlieClient, never()).getPlayers(anyList());
        verify(playerEnrichmentWriter, never()).applyPlayerDetails(anyList(), any(Instant.class));
    }

    @Test
    void poll_shouldFetchInChunksAndApplyDetails() {
        List<Player> stubs = LongStream.rangeClosed(1, 150).mapToObj(PlayerEnrichmentPollerTest::stubPlayer).toList();
        when(playerRepository.findByFullNameIsNull(PageRequest.of(0, 300))).thenReturn(stubs);
        when(balldontlieClient.getPlayers(anyList()))
                .thenReturn(List.of(new BdlPlayer(1L, "Logan Gilbert", "Logan", "Gilbert", "SP", null)));
        when(playerEnrichmentWriter.applyPlayerDetails(anyList(), eq(now))).thenReturn(1);

        poller.poll();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(balldontlieClient, times(2)).getPlayers(chunkCaptor.capture());
        assertThat(chunkCaptor.getAllValues().get(0)).hasSize(100);
        assertThat(chunkCaptor.getAllValues().get(1)).hasSize(50);
        verify(playerEnrichmentWriter, times(2)).applyPlayerDetails(anyList(), eq(now));
    }

    @Test
    void poll_shouldBackOffOnRateLimitAndSkipNextRun() {
        when(playerRepository.findByFullNameIsNull(PageRequest.of(0, 300)))
                .thenReturn(List.of(stubPlayer(1L)));
        when(balldontlieClient.getPlayers(anyList()))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        poller.poll();
        poller.poll();

        verify(balldontlieClient, times(1)).getPlayers(anyList());
        verify(playerRepository, times(1)).findByFullNameIsNull(PageRequest.of(0, 300));
    }

    @Test
    void poll_shouldPropagateNonBackoffException() {
        when(playerRepository.findByFullNameIsNull(PageRequest.of(0, 300)))
                .thenReturn(List.of(stubPlayer(1L)));
        when(balldontlieClient.getPlayers(anyList())).thenThrow(new IllegalStateException("파싱 실패"));

        assertThatThrownBy(poller::poll).isInstanceOf(IllegalStateException.class);
    }

    private static Player stubPlayer(long playerId) {
        Player player = new Player();
        player.setId(playerId);
        return player;
    }

    private static PollerProperties properties() {
        return new PollerProperties(
                true,
                Duration.ofSeconds(20),
                Duration.ofSeconds(75),
                Duration.ofMinutes(10),
                Duration.ofMinutes(5),
                Duration.ofMinutes(10),
                Duration.ofMinutes(15),
                Duration.ofSeconds(20),
                0,
                0,
                9,
                5,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                1000,
                Duration.ofHours(1),
                Duration.ofMinutes(15),
                Duration.ofMinutes(30),
                10,
                new PollerProperties.PaArchive(null, null)
        );
    }
}
