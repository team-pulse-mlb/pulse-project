package com.pulse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = "spring.flyway.enabled=false")
class GameEventRepositoryTest {

    private static final Instant SINCE = Instant.parse("2026-07-15T00:00:00Z");

    @Autowired
    private GameEventRepository gameEventRepository;

    @Test
    void protectedRetryTargets_shouldSelectOnlyMissingCopyBelowCapInsideWindow() {
        GameEvent target = saveEvent(1L, GameEvent.SPOILER_PROTECTED_SAFE, SINCE);
        GameEvent copied = saveEvent(2L, GameEvent.SPOILER_PROTECTED_SAFE, SINCE.plusSeconds(120));
        copied.setCopyProtected("이미 생성된 문구");
        GameEvent capped = saveEvent(3L, GameEvent.SPOILER_PROTECTED_SAFE, SINCE.plusSeconds(180));
        capped.setCopyProtectedAttempts(3);
        saveEvent(4L, GameEvent.SPOILER_PROTECTED_SAFE, SINCE.minusSeconds(1));
        saveEvent(5L, GameEvent.SPOILER_REVEALED_ONLY, SINCE.plusSeconds(240));
        gameEventRepository.flush();

        List<GameEvent> result = gameEventRepository.findProtectedCopyRetryTargets(
                3, SINCE, PageRequest.of(0, 50));

        assertThat(result).extracting(GameEvent::getId).containsExactly(target.getId());
    }

    private GameEvent saveEvent(long sourceRef, String spoilerLevel, Instant observedAt) {
        GameEvent event = new GameEvent();
        event.setGameId(100L);
        event.setEventType("long_at_bat");
        event.setSpoilerLevel(spoilerLevel);
        event.setSourceType(GameEvent.SOURCE_TYPE_PA);
        event.setSourceRef(sourceRef);
        event.setObservedAt(observedAt);
        return gameEventRepository.save(event);
    }
}
