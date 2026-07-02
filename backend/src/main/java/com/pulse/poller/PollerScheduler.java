package com.pulse.poller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * poller 프로필에서만 동작하는 폴링 스케줄러.
 *
 * TODO(예은): 적응형 폴링 티어 — 기본 점수에 따라 경기별 /plays 주기를
 *   5~60초로 조절하고, 승격/강등에 이력현상(hysteresis)을 적용한다.
 * TODO(예은): 429 응답 시 지수 backoff.
 */
@Component
@Profile("poller")
@RequiredArgsConstructor
@Slf4j
public class PollerScheduler {

    private final GameSyncService gameSyncService;
    private final PlaySyncService playSyncService;

    @Scheduled(fixedDelayString = "${pulse.poller.games-delay-ms}")
    public void pollGames() {
        try {
            gameSyncService.syncTodayGames();
        } catch (Exception e) {
            log.error("games polling failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${pulse.poller.plays-delay-ms}", initialDelay = 5000)
    public void pollPlays() {
        try {
            playSyncService.syncLiveGamePlays();
        } catch (Exception e) {
            log.error("plays polling failed", e);
        }
    }
}
