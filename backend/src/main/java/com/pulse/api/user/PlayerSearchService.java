package com.pulse.api.user;

import com.pulse.api.user.dto.PlayerSearchResponse;
import com.pulse.common.client.BaseballDataSource;
import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.Team;
import com.pulse.domain.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 관심 선수 등록 화면에서 사용하는 선수 검색 서비스입니다.
 *
 * 검색 순서:
 * 1. 로컬 players 테이블 검색
 * 2. 로컬 결과가 없으면 balldontlie 선수 검색
 * 3. 외부 검색 결과를 players 테이블에 캐시
 * 4. 프론트 응답 DTO로 변환
 */
@Service
@RequiredArgsConstructor
public class PlayerSearchService {

    /**
     * 검색 결과 최대 개수입니다.
     */
    private static final int MAX_SEARCH_RESULTS = 20;

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;

    /**
     * 운영 환경에서는 BalldontlieClient,
     * 시뮬레이션 환경에서는 SimulationBaseballDataSource가 주입됩니다.
     */
    private final BaseballDataSource baseballDataSource;

    /**
     * 외부 검색 결과를 players 테이블에 저장하는 전용 Writer입니다.
     */
    private final PlayerSearchCacheWriter playerSearchCacheWriter;

    /**
     * 선수 영문 이름으로 검색합니다.
     *
     * @param search 사용자가 입력한 영문 선수명
     * @return 최대 20명의 선수 검색 결과
     */
    public List<PlayerSearchResponse> searchPlayers(
            String search
    ) {
        String keyword = search == null
                ? ""
                : search.trim();

        /*
         * 빈 검색어로 전체 선수 또는 외부 API를 조회하지 않습니다.
         */
        if (keyword.isBlank()) {
            return List.of();
        }

        /*
         * 먼저 로컬 DB를 검색합니다.
         *
         * 이전에 검색하거나 Poller가 수집한 선수는
         * 외부 API를 다시 호출하지 않고 바로 반환합니다.
         */
        List<Player> players = searchLocalPlayers(
                keyword
        );

        /*
         * 로컬 DB에 검색 결과가 하나도 없을 때만
         * 외부 balldontlie API를 호출합니다.
         */
        if (players.isEmpty()) {
            List<BdlPlayer> externalPlayers =
                    baseballDataSource.searchPlayers(
                            keyword,
                            MAX_SEARCH_RESULTS
                    );

            players =
                    playerSearchCacheWriter
                            .upsertSearchResults(
                                    externalPlayers,
                                    Instant.now()
                            )
                            .stream()
                            /*
                             * 외부 API 응답도 최종적으로 검색어와
                             * 일치하는 이름만 반환합니다.
                             */
                            .filter(player ->
                                    containsIgnoreCase(
                                            player.getFullName(),
                                            keyword
                                    )
                            )
                            .sorted(
                                    Comparator.comparing(
                                            Player::getFullName,
                                            String.CASE_INSENSITIVE_ORDER
                                    )
                            )
                            .limit(MAX_SEARCH_RESULTS)
                            .toList();
        }

        return toResponses(players);
    }

    /**
     * 로컬 players 테이블에서 선수 이름을 검색합니다.
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
     * Player 목록을 프론트 응답 DTO로 변환합니다.
     *
     * 선수마다 팀을 개별 조회하지 않고,
     * 필요한 팀 목록을 한 번에 가져옵니다.
     */
    private List<PlayerSearchResponse> toResponses(
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
                        PlayerSearchResponse.from(
                                player,
                                teamsById.get(
                                        player.getTeamId()
                                )
                        )
                )
                .toList();
    }

    /**
     * null에 안전한 대소문자 무시 부분 문자열 검사입니다.
     */
    private boolean containsIgnoreCase(
            String source,
            String keyword
    ) {
        return source != null &&
                source.toLowerCase()
                        .contains(
                                keyword.toLowerCase()
                        );
    }
}