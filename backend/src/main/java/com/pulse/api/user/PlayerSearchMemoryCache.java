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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

        SearchCacheEntry entry =
                searchEntries.get(cacheKey);

        if (entry == null) {
            return Optional.empty();
        }

        if (entry.isExpired()) {
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

            if (entry.isExpired()) {
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

        long expiresAtNanos =
                System.nanoTime() + ttl.toNanos();

        List<BdlPlayer> immutablePlayers =
                List.copyOf(players);

        searchEntries.put(
                cacheKey,
                new SearchCacheEntry(
                        immutablePlayers,
                        expiresAtNanos
                )
        );

        /*
         * 검색된 선수는 등록 API에서 playerId로 재사용할 수 있도록
         * 별도 인덱스에도 같은 만료 시간으로 보관합니다.
         */
        for (BdlPlayer player : immutablePlayers) {
            playerEntries.put(
                    player.id(),
                    new PlayerCacheEntry(
                            player,
                            expiresAtNanos
                    )
            );
        }
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
            long expiresAtNanos
    ) {

        private boolean isExpired() {
            return System.nanoTime() >= expiresAtNanos;
        }
    }

    /**
     * playerId 단위 캐시 항목입니다.
     */
    private record PlayerCacheEntry(
            BdlPlayer player,
            long expiresAtNanos
    ) {

        private boolean isExpired() {
            return System.nanoTime() >= expiresAtNanos;
        }
    }
}
