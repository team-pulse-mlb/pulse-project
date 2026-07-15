package com.pulse.api.user;

import com.pulse.api.user.dto.PlayerSearchResponse;
import com.pulse.api.user.dto.PlayerSearchResultResponse;
import com.pulse.common.client.BaseballDataSource;
import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.Team;
import com.pulse.domain.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관심 선수 등록 화면에서 사용하는 선수 검색 서비스입니다.
 *
 * 검색 순서:
 * 1. 검색어 정규화
 * 2. 인메모리 TTL 캐시 확인
 * 3. 캐시 미스 시 외부 balldontlie 이름 검색
 * 4. 같은 playerId의 로컬 정보만 보조 병합
 * 5. 외부 장애 시 로컬 players 검색으로 폴백
 *
 * 이 서비스는 players 테이블에 데이터를 저장하지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerSearchService {

    /**
     * 외부 API에 요청할 최대 검색 결과 개수입니다.
     */
    private static final int MAX_SEARCH_RESULTS = 20;

    /**
     * 외부 API 쿼터 보호를 위한 최소 검색어 길이입니다.
     */
    private static final int MIN_SEARCH_LENGTH = 2;

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final BaseballDataSource baseballDataSource;
    private final PlayerSearchMemoryCache playerSearchMemoryCache;

    /**
     * 선수 영문 이름을 검색합니다.
     *
     * 외부 검색이 성공하면 complete=true,
     * 외부 검색이 실패하고 로컬 결과로 폴백하면 complete=false입니다.
     */
    public PlayerSearchResultResponse searchPlayers(
            String search
    ) {
        String keyword = normalizeKeyword(search);

        /*
         * 너무 짧은 검색어는 외부 API에 보내지 않습니다.
         *
         * 외부 호출을 하지 않았지만 장애가 발생한 것은 아니므로
         * complete=true의 빈 목록으로 반환합니다.
         */
        if (keyword.length() < MIN_SEARCH_LENGTH) {
            return PlayerSearchResultResponse.complete(
                    List.of()
            );
        }

        try {
            List<BdlPlayer> externalPlayers =
                    playerSearchMemoryCache.getOrLoad(
                            keyword,
                            () -> baseballDataSource.searchPlayers(
                                    keyword,
                                    MAX_SEARCH_RESULTS
                            )
                    );

            List<BdlPlayer> normalizedPlayers =
                    normalizeExternalPlayers(
                            externalPlayers,
                            keyword
                    );

            return PlayerSearchResultResponse.complete(
                    toExternalResponses(normalizedPlayers)
            );
        } catch (RuntimeException exception) {
            /*
             * 429, 타임아웃, 네트워크 오류 등으로 외부 검색이 실패해도
             * 검색 API 전체를 500으로 종료하지 않습니다.
             *
             * 로컬 players는 일부 선수만 가진 불완전한 데이터이므로
             * complete=false를 함께 반환합니다.
             */
            log.warn(
                    "외부 선수 검색 실패. 로컬 검색으로 폴백합니다: keyword={}",
                    keyword,
                    exception
            );

            List<Player> localPlayers =
                    searchLocalPlayers(keyword);

            return PlayerSearchResultResponse.incomplete(
                    toLocalResponses(localPlayers)
            );
        }
    }

    /**
     * 외부 검색 결과를 ID 기준으로 중복 제거하고,
     * 검색어와 일치하는 선수만 정렬해서 반환합니다.
     */
    private List<BdlPlayer> normalizeExternalPlayers(
            List<BdlPlayer> externalPlayers,
            String keyword
    ) {
        if (externalPlayers == null
                || externalPlayers.isEmpty()) {

            return List.of();
        }

        Map<Long, BdlPlayer> playerById =
                new LinkedHashMap<>();

        for (BdlPlayer player : externalPlayers) {
            if (player == null
                    || player.id() == null
                    || player.id() <= 0
                    || !containsIgnoreCase(
                    player.fullName(),
                    keyword
            )) {

                continue;
            }

            playerById.putIfAbsent(
                    player.id(),
                    player
            );
        }

        return playerById.values()
                .stream()
                .sorted(
                        Comparator.comparing(
                                BdlPlayer::fullName,
                                String.CASE_INSENSITIVE_ORDER
                        )
                )
                .limit(MAX_SEARCH_RESULTS)
                .toList();
    }

    /**
     * 외부 API 장애 시 사용하는 로컬 검색입니다.
     *
     * 로컬 players는 전체 선수 명단이 아니므로
     * 이 결과만 반환할 때는 complete=false입니다.
     */
    private List<Player> searchLocalPlayers(
            String keyword
    ) {
        return playerRepository
                .findByFullNameIsNotNullAndFullNameContainingIgnoreCaseOrderByFullNameAsc(
                        keyword,
                        PageRequest.of(
                                0,
                                MAX_SEARCH_RESULTS
                        )
                );
    }

    /**
     * 외부 검색 결과를 프론트 응답으로 변환합니다.
     *
     * 동일 ID의 로컬 Player가 존재하면
     * 외부 응답의 누락 정보만 보조합니다.
     */
    private List<PlayerSearchResponse> toExternalResponses(
            List<BdlPlayer> externalPlayers
    ) {
        if (externalPlayers == null
                || externalPlayers.isEmpty()) {

            return List.of();
        }

        List<Long> playerIds = externalPlayers.stream()
                .map(BdlPlayer::id)
                .toList();

        Map<Long, Player> localPlayersById =
                playerRepository
                        .findAllById(playerIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Player::getId,
                                        Function.identity()
                                )
                        );

        Set<Long> teamIds = externalPlayers.stream()
                .map(externalPlayer ->
                        resolveTeamId(
                                externalPlayer,
                                localPlayersById.get(
                                        externalPlayer.id()
                                )
                        )
                )
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Team> teamsById =
                teamRepository
                        .findAllById(teamIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Team::getTeamId,
                                        Function.identity()
                                )
                        );

        return externalPlayers.stream()
                .map(externalPlayer -> {
                    Player localPlayer =
                            localPlayersById.get(
                                    externalPlayer.id()
                            );

                    Long teamId = resolveTeamId(
                            externalPlayer,
                            localPlayer
                    );

                    return PlayerSearchResponse.fromExternal(
                            externalPlayer,
                            localPlayer,
                            teamsById.get(teamId)
                    );
                })
                .toList();
    }

    /**
     * 외부 장애 시 로컬 선수 목록을 응답으로 변환합니다.
     */
    private List<PlayerSearchResponse> toLocalResponses(
            List<Player> players
    ) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }

        Set<Long> teamIds = players.stream()
                .map(Player::getTeamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Team> teamsById =
                teamRepository
                        .findAllById(teamIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Team::getTeamId,
                                        Function.identity()
                                )
                        );

        return players.stream()
                .map(player ->
                        PlayerSearchResponse.fromLocal(
                                player,
                                teamsById.get(
                                        player.getTeamId()
                                )
                        )
                )
                .toList();
    }

    /**
     * 외부 팀 ID를 우선 사용하고,
     * 외부에 팀 정보가 없을 때만 로컬 값을 사용합니다.
     */
    private Long resolveTeamId(
            BdlPlayer externalPlayer,
            Player localPlayer
    ) {
        if (externalPlayer.team() != null
                && externalPlayer.team().id() != null
                && externalPlayer.team().id() > 0) {

            return externalPlayer.team().id();
        }

        return localPlayer == null
                ? null
                : localPlayer.getTeamId();
    }

    /**
     * 검색어 앞뒤 공백을 제거하고 소문자로 통일합니다.
     */
    private String normalizeKeyword(
            String search
    ) {
        return search == null
                ? ""
                : search
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * null에 안전한 대소문자 무시 부분 문자열 검사입니다.
     */
    private boolean containsIgnoreCase(
            String source,
            String keyword
    ) {
        return source != null
                && source
                .toLowerCase(Locale.ROOT)
                .contains(keyword);
    }
}
