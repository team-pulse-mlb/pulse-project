package com.pulse.api.user;

import com.pulse.common.client.BdlDtos.BdlPlayer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 관심 선수 검색 결과를 짧게 보관하는 인메모리 TTL 캐시입니다.
 *
 * 중요한 점:
 * - PostgreSQL players 테이블에 저장하지 않습니다.
 * - 애플리케이션 재시작 시 캐시는 모두 사라집니다.
 * - 동일 검색어가 짧은 시간 안에 반복될 때 외부 API 호출을 줄입니다.
 * - 검색된 선수는 playerId로도 조회할 수 있게 보관합니다.
 *
 * 등록 API에서는 먼저 playerId 캐시를 확인하고,
 * 캐시에 없을 때만 외부 getPlayers(ids)를 호출할 예정입니다.
 */
@Component
public class PlayerSearchMemoryCache {

    /**
     * 정상 검색 결과의 보관 시간입니다.
     *
     * 검색 화면에서 같은 검색어를 반복할 가능성은 높지만,
     * 선수 소속과 상태가 영구적으로 동일하다고 볼 수 없으므로
     * 장시간 보관하지 않습니다.
     */
    private static final Duration RESULT_TTL =
            Duration.ofMinutes(5);

    /**
     * 검색 결과가 0건인 경우의 보관 시간입니다.
     *
     * 오타나 순간적인 외부 데이터 상태 때문에 결과가 없을 수 있으므로
     * 정상 결과보다 짧게 보관합니다.
     */
    private static final Duration EMPTY_RESULT_TTL =
            Duration.ofSeconds(30);

    /**
     * 검색어 단위 캐시가 보관할 수 있는 최대 개수입니다.
     *
     * 검색어가 계속 달라져도 searchEntries가
     * 제한 없이 증가하지 않도록 상한을 둡니다.
     */
    private static final int MAX_SEARCH_ENTRY_COUNT = 500;

    /**
     * 선수 ID 단위 보조 캐시가 보관할 수 있는 최대 개수입니다.
     *
     * 한 검색 결과에 여러 선수가 들어갈 수 있으므로
     * 검색어 캐시보다 넉넉한 크기로 제한합니다.
     */
    private static final int MAX_PLAYER_ENTRY_COUNT = 5_000;

    /**
     * 만료 삭제와 최대 개수 제한을 한 번에 처리하기 위한 잠금입니다.
     *
     * 캐시 조회 자체는 ConcurrentHashMap으로 동시에 처리하지만,
     * 여러 요청이 동시에 오래된 항목을 제거할 때
     * 최대 개수 제한이 크게 어긋나지 않도록 정리 작업만 묶습니다.
     */
    private final Object maintenanceLock = new Object();

    /**
     * 현재 단조 증가 시간을 나노초로 제공합니다.
     *
     * 운영 환경에서는 System.nanoTime()을 사용하고,
     * 테스트에서는 시간을 직접 움직일 수 있는 가짜 공급자를 넣습니다.
     */
    private final LongSupplier nanoTimeSource;

    /**
     * Spring이 운영 환경에서 사용하는 기본 생성자입니다.
     */
    public PlayerSearchMemoryCache() {
        this(System::nanoTime);
    }

    /**
     * 테스트에서 가짜 시간을 주입하기 위한 생성자입니다.
     *
     * 같은 패키지의 테스트에서만 직접 사용할 수 있도록
     * package-private 접근 범위로 둡니다.
     */
    PlayerSearchMemoryCache(
            LongSupplier nanoTimeSource
    ) {
        this.nanoTimeSource =
                Objects.requireNonNull(nanoTimeSource);
    }

    /**
     * 정규화된 검색어 → 검색 결과 캐시입니다.
     */
    private final ConcurrentMap<String, SearchCacheEntry>
            searchEntries = new ConcurrentHashMap<>();

    /**
     * playerId → 선수 정보 캐시입니다.
     *
     * 관심 선수 등록 시 검색 결과를 다시 활용하기 위한 인덱스입니다.
     */
    private final ConcurrentMap<Long, PlayerCacheEntry>
            playerEntries = new ConcurrentHashMap<>();

    /**
     * 동일 검색어가 동시에 들어왔을 때
     * 하나의 외부 호출 결과를 함께 기다리기 위한 저장소입니다.
     */
    private final ConcurrentMap<
            String,
            CompletableFuture<List<BdlPlayer>>
            > inFlightSearches = new ConcurrentHashMap<>();

    /**
     * 캐시를 먼저 확인하고,
     * 캐시가 없을 때만 loader를 한 번 실행합니다.
     *
     * 같은 검색어 요청이 동시에 여러 개 들어와도
     * 외부 API는 하나의 요청만 실행됩니다.
     */
    public List<BdlPlayer> getOrLoad(
            String keyword,
            Supplier<List<BdlPlayer>> loader
    ) {
        String cacheKey = normalizeKey(keyword);

        Optional<List<BdlPlayer>> cached =
                findSearchResults(cacheKey);

        if (cached.isPresent()) {
            return cached.get();
        }

        CompletableFuture<List<BdlPlayer>> newFuture =
                new CompletableFuture<>();

        CompletableFuture<List<BdlPlayer>> runningFuture =
                inFlightSearches.putIfAbsent(
                        cacheKey,
                        newFuture
                );

        /*
         * 이미 같은 검색어를 조회 중인 요청이 있다면
         * 새 외부 호출을 만들지 않고 기존 결과를 기다립니다.
         */
        if (runningFuture != null) {
            return await(runningFuture);
        }

        try {
            List<BdlPlayer> loadedPlayers =
                    sanitize(loader.get());

            putSearchResults(
                    cacheKey,
                    loadedPlayers
            );

            newFuture.complete(loadedPlayers);

            return loadedPlayers;
        } catch (RuntimeException exception) {
            newFuture.completeExceptionally(exception);
            throw exception;
        } catch (Error error) {
            newFuture.completeExceptionally(error);
            throw error;
        } finally {
            inFlightSearches.remove(
                    cacheKey,
                    newFuture
            );
        }
    }

    /**
     * 검색어에 해당하는 유효한 캐시를 조회합니다.
     *
     * 빈 목록도 정상 캐시 값이므로 Optional.of(emptyList)가
     * 반환될 수 있습니다.
     */
    public Optional<List<BdlPlayer>> findSearchResults(
            String keyword
    ) {
        String cacheKey = normalizeKey(keyword);
        long nowNanos = nowNanos();

        /*
         * 현재 검색어만 확인하는 것이 아니라
         * 다른 검색어의 만료 항목도 함께 정리합니다.
         *
         * 따라서 만료된 검색어가 다시 요청되지 않아도
         * 새로운 캐시 요청이 들어오면 제거됩니다.
         */
        removeExpiredEntries(nowNanos);

        SearchCacheEntry entry =
                searchEntries.get(cacheKey);

        if (entry == null) {
            return Optional.empty();
        }

        /*
         * 정리 직후 다른 스레드 또는 시간이 변한 상황까지
         * 안전하게 처리하기 위한 추가 확인입니다.
         */
        if (entry.isExpired(nowNanos)) {
            searchEntries.remove(
                    cacheKey,
                    entry
            );

            return Optional.empty();
        }

        return Optional.of(entry.players());
    }

    /**
     * 관심 선수 등록 요청에 포함된 playerId 목록을
     * 검색 캐시에서 조회합니다.
     *
     * 캐시에 없는 ID는 결과 Map에 포함되지 않습니다.
     */
    public Map<Long, BdlPlayer> findPlayersByIds(
            Collection<Long> playerIds
    ) {
        if (playerIds == null || playerIds.isEmpty()) {
            return Map.of();
        }

        long nowNanos = nowNanos();

        /*
         * 요청한 선수 ID뿐 아니라 전체 캐시의 만료 항목을 정리합니다.
         */
        removeExpiredEntries(nowNanos);

        Map<Long, BdlPlayer> result =
                new LinkedHashMap<>();

        for (Long playerId : playerIds) {
            if (playerId == null || playerId <= 0) {
                continue;
            }

            PlayerCacheEntry entry =
                    playerEntries.get(playerId);

            if (entry == null) {
                continue;
            }

            if (entry.isExpired(nowNanos)) {
                playerEntries.remove(
                        playerId,
                        entry
                );

                continue;
            }

            result.put(
                    playerId,
                    entry.player()
            );
        }

        return result;
    }

    /**
     * 검색 결과와 playerId 인덱스를 함께 저장합니다.
     */
        private void putSearchResults(
                String cacheKey,
                List<BdlPlayer> players
) {
            Duration ttl = players.isEmpty()
                    ? EMPTY_RESULT_TTL
                    : RESULT_TTL;

            /*
             * 캐시에 저장하는 현재 시각입니다.
             *
             * 테스트에서는 nanoTimeSource에 가짜 시간을 넣을 수 있고,
             * 실제 운영에서는 System.nanoTime()이 사용됩니다.
             */
            long cachedAtNanos = nowNanos();

            /*
             * 캐시 만료 시각입니다.
             *
             * 현재 시각 + TTL로 계산합니다.
             */
            long expiresAtNanos =
                    cachedAtNanos + ttl.toNanos();

            List<BdlPlayer> immutablePlayers =
                    List.copyOf(players);

            /*
             * 만료 데이터 정리, 신규 저장, 최대 개수 제한을
             * 동시에 여러 스레드가 실행하지 않도록 묶습니다.
             */
            synchronized (maintenanceLock) {

                /*
                 * 새로운 데이터를 저장하기 전에
                 * 이미 만료된 항목부터 전체 정리합니다.
                 */
                removeExpiredEntriesWithoutLock(
                        cachedAtNanos
                );

                /*
                 * 검색어 기준 결과 캐시에 저장합니다.
                 *
                 * record의 인자는 다음 순서입니다.
                 * 1. 검색 결과
                 * 2. 저장 시각
                 * 3. 만료 시각
                 */
                searchEntries.put(
                        cacheKey,
                        new SearchCacheEntry(
                                immutablePlayers,
                                cachedAtNanos,
                                expiresAtNanos
                        )
                );

                /*
                 * 검색 결과에 포함된 선수들을
                 * playerId 기준 보조 캐시에도 저장합니다.
                 */
                for (BdlPlayer player : immutablePlayers) {
                    playerEntries.put(
                            player.id(),
                            new PlayerCacheEntry(
                                    player,
                                    cachedAtNanos,
                                    expiresAtNanos
                            )
                    );
                }

                /*
                 * 최대 보관 개수를 초과하면
                 * 가장 오래된 캐시부터 제거합니다.
                 */
                removeOldestSearchEntriesIfNeeded();
                removeOldestPlayerEntriesIfNeeded();
            }
        }


    /**
     * 검색어 캐시와 선수 ID 캐시에서
     * 만료된 모든 항목을 정리합니다.
     */
    private void removeExpiredEntries(
            long nowNanos
    ) {
        synchronized (maintenanceLock) {
            removeExpiredEntriesWithoutLock(
                    nowNanos
            );
        }
    }

    /**
     * maintenanceLock을 이미 획득한 상태에서
     * 만료 항목을 실제로 제거합니다.
     */
    private void removeExpiredEntriesWithoutLock(
            long nowNanos
    ) {
        searchEntries.forEach((key, entry) -> {
            if (entry.isExpired(nowNanos)) {
                searchEntries.remove(
                        key,
                        entry
                );
            }
        });

        playerEntries.forEach((playerId, entry) -> {
            if (entry.isExpired(nowNanos)) {
                playerEntries.remove(
                        playerId,
                        entry
                );
            }
        });
    }

    /**
     * 검색어 캐시가 최대 개수를 초과하면
     * 가장 먼저 저장된 항목부터 제거합니다.
     */
    private void removeOldestSearchEntriesIfNeeded() {
        while (searchEntries.size()
                > MAX_SEARCH_ENTRY_COUNT) {

            Map.Entry<String, SearchCacheEntry> oldestEntry =
                    null;

            for (Map.Entry<String, SearchCacheEntry> candidate
                    : searchEntries.entrySet()) {

                if (oldestEntry == null
                        || candidate.getValue()
                        .cachedAtNanos()
                        < oldestEntry.getValue()
                        .cachedAtNanos()) {

                    oldestEntry = candidate;
                }
            }

            if (oldestEntry == null) {
                return;
            }

            searchEntries.remove(
                    oldestEntry.getKey(),
                    oldestEntry.getValue()
            );
        }
    }

    /**
     * 선수 ID 보조 캐시가 최대 개수를 초과하면
     * 가장 먼저 저장된 항목부터 제거합니다.
     */
    private void removeOldestPlayerEntriesIfNeeded() {
        while (playerEntries.size()
                > MAX_PLAYER_ENTRY_COUNT) {

            Map.Entry<Long, PlayerCacheEntry> oldestEntry =
                    null;

            for (Map.Entry<Long, PlayerCacheEntry> candidate
                    : playerEntries.entrySet()) {

                if (oldestEntry == null
                        || candidate.getValue()
                        .cachedAtNanos()
                        < oldestEntry.getValue()
                        .cachedAtNanos()) {

                    oldestEntry = candidate;
                }
            }

            if (oldestEntry == null) {
                return;
            }

            playerEntries.remove(
                    oldestEntry.getKey(),
                    oldestEntry.getValue()
            );
        }
    }

    /**
     * 현재 캐시 시간 값을 반환합니다.
     */
    private long nowNanos() {
        return nanoTimeSource.getAsLong();
    }


    /**
     * 외부 응답에서 사용할 수 없는 값과 중복 ID를 제거합니다.
     */
    private List<BdlPlayer> sanitize(
            List<BdlPlayer> players
    ) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }

        Map<Long, BdlPlayer> uniquePlayers =
                new LinkedHashMap<>();

        for (BdlPlayer player : players) {
            if (player == null
                    || player.id() == null
                    || player.id() <= 0
                    || player.fullName() == null
                    || player.fullName().isBlank()) {

                continue;
            }

            uniquePlayers.putIfAbsent(
                    player.id(),
                    player
            );
        }

        return List.copyOf(
                new ArrayList<>(
                        uniquePlayers.values()
                )
        );
    }

    /**
     * 다른 요청이 실행 중인 동일 검색 결과를 기다립니다.
     *
     * 원래 외부 호출에서 발생한 RuntimeException을
     * CompletionException 안에 숨기지 않고 다시 전달합니다.
     */
    private List<BdlPlayer> await(
            CompletableFuture<List<BdlPlayer>> future
    ) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            if (cause instanceof Error error) {
                throw error;
            }

            throw exception;
        }
    }

    /**
     * 검색어 캐시 키를 소문자와 trim 기준으로 통일합니다.
     */
    private String normalizeKey(
            String keyword
    ) {
        return keyword == null
                ? ""
                : keyword
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 검색어 단위 캐시 항목입니다.
     */
    private record SearchCacheEntry(
            List<BdlPlayer> players,
            long cachedAtNanos,
            long expiresAtNanos
    ) {

        private boolean isExpired(
                long nowNanos
        ) {
            return nowNanos >= expiresAtNanos;
        }
    }

    /**
     * playerId 단위 캐시 항목입니다.
     */
    private record PlayerCacheEntry(
            BdlPlayer player,
            long cachedAtNanos,
            long expiresAtNanos
    ) {

        private boolean isExpired(
                long nowNanos
        ) {
            return nowNanos >= expiresAtNanos;
        }
    }
}
