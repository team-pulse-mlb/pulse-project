package com.pulse.poller;

import com.pulse.common.client.BaseballDataSource;
import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 이름 NULL 스텁 선수 보강 저빈도 수집기. play 저장 시 만든 id-only 스텁
 * (라인업 밖 구원 투수·대타)의 이름·포지션·팀을 선수 마스터 조회로 채운다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.poller", name = "enabled", havingValue = "true")
@Slf4j
public class PlayerEnrichmentPoller {

    /** 요청당 선수 id 수 상한. per_page 최대값(100)과 맞춘다. */
    private static final int CHUNK_SIZE = 100;

    /** 런당 보강 대상 상한. 초과분은 다음 런에서 처리한다. */
    private static final int MAX_PLAYERS_PER_RUN = 300;

    private final BaseballDataSource balldontlieClient;
    private final PlayerRepository playerRepository;
    private final PlayerEnrichmentWriter playerEnrichmentWriter;
    private final PollerRateLimiter rateLimiter;
    private final Clock clock;
    private final PollerBackoff backoff;

    @Autowired
    public PlayerEnrichmentPoller(
            BaseballDataSource balldontlieClient,
            PlayerRepository playerRepository,
            PlayerEnrichmentWriter playerEnrichmentWriter,
            PollerProperties properties,
            PollerRateLimiter rateLimiter
    ) {
        this(balldontlieClient, playerRepository, playerEnrichmentWriter, properties, rateLimiter, Clock.systemUTC());
    }

    PlayerEnrichmentPoller(
            BaseballDataSource balldontlieClient,
            PlayerRepository playerRepository,
            PlayerEnrichmentWriter playerEnrichmentWriter,
            PollerProperties properties,
            PollerRateLimiter rateLimiter,
            Clock clock
    ) {
        this.balldontlieClient = balldontlieClient;
        this.playerRepository = playerRepository;
        this.playerEnrichmentWriter = playerEnrichmentWriter;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.backoff = new PollerBackoff(properties.initialBackoff(), properties.maxBackoff());
    }

    @Scheduled(fixedDelayString = "${pulse.poller.player-enrichment-delay-ms:600000}")
    public void poll() {
        Instant now = clock.instant();
        if (!backoff.canCall(now)) {
            return;
        }

        List<Long> targetIds = playerRepository.findByFullNameIsNull(PageRequest.of(0, MAX_PLAYERS_PER_RUN)).stream()
                .map(Player::getId)
                .toList();
        if (targetIds.isEmpty()) {
            return;
        }

        int updated = 0;
        Set<Long> returnedIds = new HashSet<>();
        try {
            for (int start = 0; start < targetIds.size(); start += CHUNK_SIZE) {
                List<Long> chunk = targetIds.subList(start, Math.min(start + CHUNK_SIZE, targetIds.size()));
                rateLimiter.acquire();
                List<BdlPlayer> players = balldontlieClient.getPlayers(chunk);
                for (BdlPlayer player : players) {
                    if (player.id() != null) {
                        returnedIds.add(player.id());
                    }
                }
                updated += playerEnrichmentWriter.applyPlayerDetails(players, now);
            }
            backoff.recordSuccess();
        } catch (RuntimeException e) {
            if (!PollerExceptionClassifier.shouldBackoff(e)) {
                throw e;
            }
            backoff.recordFailure(now, PollerExceptionClassifier.retryAfter(e));
            log.warn("player enrichment poll failed, backed off until {}", backoff.blockedUntil(), e);
            return;
        }

        long missing = targetIds.stream().filter(id -> !returnedIds.contains(id)).count();
        if (missing > 0) {
            log.info("player enrichment completed: targets={}, updated={}, missingFromApi={}",
                    targetIds.size(), updated, missing);
        } else {
            log.info("player enrichment completed: targets={}, updated={}", targetIds.size(), updated);
        }
    }
}
