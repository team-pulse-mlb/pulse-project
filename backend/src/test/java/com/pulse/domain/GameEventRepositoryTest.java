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
        GameEvent target = saveEvent(100L, 1L, GameEvent.SPOILER_PROTECTED_SAFE, SINCE);
        GameEvent copied = saveEvent(100L, 2L, GameEvent.SPOILER_PROTECTED_SAFE, SINCE.plusSeconds(120));
        copied.setCopyProtected("이미 생성된 문구");
        GameEvent capped = saveEvent(100L, 3L, GameEvent.SPOILER_PROTECTED_SAFE, SINCE.plusSeconds(180));
        capped.setCopyProtectedAttempts(3);
        saveEvent(100L, 4L, GameEvent.SPOILER_PROTECTED_SAFE, SINCE.minusSeconds(1));
        saveEvent(100L, 5L, GameEvent.SPOILER_REVEALED_ONLY, SINCE.plusSeconds(240));
        gameEventRepository.flush();

        List<GameEvent> result = gameEventRepository.findProtectedCopyRetryTargets(
                3, SINCE, PageRequest.of(0, 50));

        assertThat(result).extracting(GameEvent::getId).containsExactly(target.getId());
    }

    @Test
    void protectedAiReprocessTargets_shouldIncludeExistingCopiesAndRespectCursor() {
        GameEvent first = saveEvent(100L, 11L, GameEvent.SPOILER_PROTECTED_SAFE, SINCE);
        GameEvent second = saveEvent(100L, 12L, GameEvent.SPOILER_PROTECTED_SAFE, SINCE.plusSeconds(60));
        second.setCopyProtected("기존 문구");
        saveEvent(100L, 13L, GameEvent.SPOILER_REVEALED_ONLY, SINCE.plusSeconds(120));
        gameEventRepository.flush();

        long maxId = gameEventRepository.findMaxProtectedEventId();
        List<GameEvent> result = gameEventRepository.findProtectedAiReprocessTargets(
                first.getId(), maxId, PageRequest.of(0, 50));

        assertThat(maxId).isEqualTo(second.getId());
        assertThat(result).extracting(GameEvent::getId).containsExactly(second.getId());
    }

    @Test
    void bySpoilerLevelAndGameIdIn_shouldSelectOnlyRequestedGamesOrderedByGameThenTime() {
        GameEvent targetLater = saveEvent(100L, 21L, GameEvent.SPOILER_PROTECTED_SAFE, SINCE.plusSeconds(60));
        GameEvent targetEarlier = saveEvent(100L, 22L, GameEvent.SPOILER_PROTECTED_SAFE, SINCE);
        saveEvent(200L, 23L, GameEvent.SPOILER_PROTECTED_SAFE, SINCE);
        saveEvent(100L, 24L, GameEvent.SPOILER_REVEALED_ONLY, SINCE);
        gameEventRepository.flush();

        List<GameEvent> result = gameEventRepository.findBySpoilerLevelAndGameIdInOrderByGameIdAscObservedAtAsc(
                GameEvent.SPOILER_PROTECTED_SAFE, List.of(100L));

        assertThat(result).extracting(GameEvent::getId)
                .containsExactly(targetEarlier.getId(), targetLater.getId());
    }

    private GameEvent saveEvent(long gameId, long sourceRef, String spoilerLevel, Instant observedAt) {
        GameEvent event = new GameEvent();
        event.setGameId(gameId);
        event.setEventType("long_at_bat");
        event.setSpoilerLevel(spoilerLevel);
        event.setSourceType(GameEvent.SOURCE_TYPE_PA);
        event.setSourceRef(sourceRef);
        event.setObservedAt(observedAt);
        return gameEventRepository.save(event);
    }
}
