package com.pulse.poller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerStubWriterTest {

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private PlayerStubWriter playerStubWriter;

    @Test
    void ensurePlayerExists_shouldNotInsertStubWhenPlayerAlreadyExists() {
        Instant observedAt = Instant.parse("2026-07-08T00:00:00Z");
        when(playerRepository.existsById(7L)).thenReturn(true);

        playerStubWriter.ensurePlayerExists(7L, observedAt);

        verify(playerRepository, never()).save(org.mockito.ArgumentMatchers.any(Player.class));
    }
}
