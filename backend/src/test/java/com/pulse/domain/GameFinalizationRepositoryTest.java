package com.pulse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = "spring.flyway.enabled=false")
class GameFinalizationRepositoryTest {

    @Autowired
    private GameRepository gameRepository;

    @Test
    @DisplayName("종료 처리는 games 행을 원자 갱신해 한 번만 획득한다")
    void markFinalized_shouldUpdateOnlyOnce() {
        Game game = new Game();
        game.setId(100L);
        game.setStatus(Game.STATUS_FINAL);
        gameRepository.saveAndFlush(game);
        Instant processedAt = Instant.parse("2026-07-08T04:00:00Z");

        int firstUpdate = gameRepository.markFinalized(100L, processedAt);
        int duplicateUpdate = gameRepository.markFinalized(100L, processedAt.plusSeconds(1));

        assertThat(firstUpdate).isEqualTo(1);
        assertThat(duplicateUpdate).isZero();
    }
}
