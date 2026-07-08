package com.pulse.common.client;

import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.client.BdlDtos.BdlLineup;
import com.pulse.common.client.BdlDtos.BdlOdds;
import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.common.client.BdlDtos.BdlPlayerSeasonStat;
import com.pulse.common.client.BdlDtos.BdlStanding;
import com.pulse.common.client.BdlDtos.ListResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * balldontlie MLB API 클라이언트. 외부 API 호출은 반드시 이 클래스를 거친다.
 */
@Component
public class BalldontlieClient {

    private static final int PER_PAGE = 100;

    private final RestClient restClient;

    public BalldontlieClient(BdlProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .build();
    }

    /** 특정 날짜의 경기 목록 */
    public List<BdlGame> getGames(LocalDate date) {
        ListResponse<BdlGame> response = restClient.get()
                .uri(uri -> uri.path("/mlb/v1/games")
                        .queryParam("dates[]", date.toString())
                        .queryParam("per_page", PER_PAGE)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return response == null || response.data() == null ? List.of() : response.data();
    }

    /**
     * 경기의 play 목록 한 페이지. cursor가 null이면 처음부터,
     * 아니면 해당 커서 이후부터 조회한다 (증분 수집).
     */
    public ListResponse<BdlPlay> getPlays(long gameId, Long cursor) {
        return restClient.get()
                .uri(uri -> {
                    var builder = uri.path("/mlb/v1/plays")
                            .queryParam("game_id", gameId)
                            .queryParam("per_page", PER_PAGE);
                    if (cursor != null) {
                        builder = builder.queryParam("cursor", cursor);
                    }
                    return builder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    /** 경기들의 라인업·선발 예상 투수 목록 */
    public List<BdlLineup> getLineups(List<Long> gameIds) {
        if (gameIds == null || gameIds.isEmpty()) {
            return List.of();
        }
        ListResponse<BdlLineup> response = restClient.get()
                .uri(uri -> uri.path("/mlb/v1/lineups")
                        .queryParam("game_ids[]", gameIds.toArray())
                        .queryParam("per_page", PER_PAGE)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return response == null || response.data() == null ? List.of() : response.data();
    }

    /** 특정 날짜 슬레이트의 배당 목록 */
    public List<BdlOdds> getOdds(LocalDate date) {
        ListResponse<BdlOdds> response = restClient.get()
                .uri(uri -> uri.path("/mlb/v1/odds")
                        .queryParam("dates[]", date.toString())
                        .queryParam("per_page", PER_PAGE)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return response == null || response.data() == null ? List.of() : response.data();
    }

    /** 시즌 순위 (일 배치) */
    public List<BdlStanding> getStandings(int season) {
        ListResponse<BdlStanding> response = restClient.get()
                .uri(uri -> uri.path("/mlb/v1/standings")
                        .queryParam("season", season)
                        .queryParam("per_page", PER_PAGE)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return response == null || response.data() == null ? List.of() : response.data();
    }

    /** 선수 시즌 누적 스탯. PREGAME task 발행 전 선발 투수 대상 온디맨드 조회에 쓴다. */
    public List<BdlPlayerSeasonStat> getPlayerSeasonStats(int season, List<Long> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return List.of();
        }
        ListResponse<BdlPlayerSeasonStat> response = restClient.get()
                .uri(uri -> uri.path("/mlb/v1/season_stats")
                        .queryParam("season", season)
                        .queryParam("player_ids[]", playerIds.toArray())
                        .queryParam("per_page", PER_PAGE)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return response == null || response.data() == null ? List.of() : response.data();
    }

    /** 경기의 plate appearance 전체 목록. 이 endpoint는 증분 cursor가 없다. */
    public List<BdlPlateAppearance> getPlateAppearances(long gameId) {
        ListResponse<BdlPlateAppearance> response = restClient.get()
                .uri(uri -> uri.path("/mlb/v1/plate_appearances")
                        .queryParam("game_id", gameId)
                        .queryParam("per_page", PER_PAGE)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return response == null || response.data() == null ? List.of() : response.data();
    }
}
