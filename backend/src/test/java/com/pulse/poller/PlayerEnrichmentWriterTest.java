package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.TeamRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerEnrichmentWriterTest {

    private final Instant observedAt = Instant.parse("2026-07-11T00:00:00Z");

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private PlayerEnrichmentWriter playerEnrichmentWriter;

    @Test
    void applyPlayerDetails_shouldFillStubDetailsAndReturnUpdatedCount() {
        Player stub = stubPlayer(7L);
        when(playerRepository.findById(7L)).thenReturn(Optional.of(stub));
        when(teamRepository.existsById(12L)).thenReturn(true);
        BdlPlayer dto = new BdlPlayer(7L, "Logan Gilbert", "Logan", "Gilbert", "SP", new BdlPlayer.TeamRef(12L));

        int updated = playerEnrichmentWriter.applyPlayerDetails(List.of(dto), observedAt);

        assertThat(updated).isEqualTo(1);
        assertThat(stub.getFullName()).isEqualTo("Logan Gilbert");
        assertThat(stub.getFirstName()).isEqualTo("Logan");
        assertThat(stub.getLastName()).isEqualTo("Gilbert");
        assertThat(stub.getPosition()).isEqualTo("SP");
        assertThat(stub.getTeamId()).isEqualTo(12L);
        assertThat(stub.getUpdatedAt()).isEqualTo(observedAt);
        verify(playerRepository).save(stub);
    }

    @Test
    void applyPlayerDetails_shouldNotOverwriteExistingValuesWithNullFields() {
        Player player = stubPlayer(7L);
        player.setFullName("Logan Gilbert");
        player.setPosition("SP");
        player.setTeamId(12L);
        when(playerRepository.findById(7L)).thenReturn(Optional.of(player));
        BdlPlayer dto = new BdlPlayer(7L, null, "Logan", null, null, null);

        playerEnrichmentWriter.applyPlayerDetails(List.of(dto), observedAt);

        assertThat(player.getFullName()).isEqualTo("Logan Gilbert");
        assertThat(player.getFirstName()).isEqualTo("Logan");
        assertThat(player.getPosition()).isEqualTo("SP");
        assertThat(player.getTeamId()).isEqualTo(12L);
    }

    @Test
    void applyPlayerDetails_shouldIgnoreNegativeTeamId() {
        Player player = stubPlayer(7L);
        when(playerRepository.findById(7L)).thenReturn(Optional.of(player));
        BdlPlayer dto = new BdlPlayer(7L, "Free Agent", null, null, null, new BdlPlayer.TeamRef(-1L));

        int updated = playerEnrichmentWriter.applyPlayerDetails(List.of(dto), observedAt);

        assertThat(updated).isEqualTo(1);
        assertThat(player.getFullName()).isEqualTo("Free Agent");
        assertThat(player.getTeamId()).isNull();
        verify(teamRepository, never()).existsById(any(Long.class));
        verify(playerRepository).save(player);
    }

    @Test
    void applyPlayerDetails_shouldKeepExistingTeamWhenTeamDoesNotExist() {
        Player player = stubPlayer(7L);
        player.setTeamId(12L);
        when(playerRepository.findById(7L)).thenReturn(Optional.of(player));
        when(teamRepository.existsById(99L)).thenReturn(false);
        BdlPlayer dto = new BdlPlayer(7L, "Logan Gilbert", null, null, null, new BdlPlayer.TeamRef(99L));

        playerEnrichmentWriter.applyPlayerDetails(List.of(dto), observedAt);

        assertThat(player.getTeamId()).isEqualTo(12L);
        verify(playerRepository).save(player);
    }

    @Test
    void applyPlayerDetails_shouldSkipUnknownPlayersWithoutCreatingRows() {
        when(playerRepository.findById(9L)).thenReturn(Optional.empty());
        BdlPlayer dto = new BdlPlayer(9L, "Unknown Player", null, null, null, null);

        int updated = playerEnrichmentWriter.applyPlayerDetails(List.of(dto), observedAt);

        assertThat(updated).isZero();
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void applyPlayerDetails_shouldSkipDtoWithoutId() {
        BdlPlayer dto = new BdlPlayer(null, "No Id", null, null, null, null);

        int updated = playerEnrichmentWriter.applyPlayerDetails(List.of(dto), observedAt);

        assertThat(updated).isZero();
        verify(playerRepository, never()).save(any(Player.class));
    }

    private static Player stubPlayer(long playerId) {
        Player player = new Player();
        player.setId(playerId);
        return player;
    }
}
