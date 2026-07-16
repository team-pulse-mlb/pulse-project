package com.pulse.common.client;

import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.client.BdlDtos.BdlLineup;
import com.pulse.common.client.BdlDtos.BdlOdds;
import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.common.client.BdlDtos.BdlPlayerSeasonStat;
import com.pulse.common.client.BdlDtos.BdlStanding;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.client.BdlDtos.ListResponse;
import com.pulse.common.client.BdlDtos.PlateAppearancesRaw;
import com.pulse.common.metrics.PulseMetrics;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * balldontlie MLB API 클라이언트. 외부 API 호출은 반드시 이 클래스를 거친다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.simulation", name = "enabled", havingValue = "false", matchIfMissing = true)
public class BalldontlieClient implements BaseballDataSource {

    private static final int PER_PAGE = 100;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public BalldontlieClient(BdlProperties props, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                .requestFactory(createRequestFactory(props))
                .requestInterceptor((request, body, execution) -> {
                    String endpoint = request.getURI().getPath();
                    try {
                        var response = execution.execute(request, body);
                        int status = response.getStatusCode().value();
                        String outcome = status == 429 ? "rate_limited" : status >= 400 ? "error" : "success";
                        PulseMetrics.increment("pulse.bdl.requests", "endpoint", endpoint, "outcome", outcome);
                        if (status == 429) {
                            PulseMetrics.increment("pulse.bdl.rate.limit", "endpoint", endpoint);
                        }
                        return response;
                    } catch (IOException | RuntimeException exception) {
                        PulseMetrics.increment("pulse.bdl.requests", "endpoint", endpoint, "outcome", "exception");
                        throw exception;
                    }
                })
                .build();
        this.objectMapper = objectMapper;
    }

    private SimpleClientHttpRequestFactory createRequestFactory(BdlProperties props) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(props.connectTimeout());
        requestFactory.setReadTimeout(props.readTimeout());
        return requestFactory;
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

    /** 경기들의 배당 목록 */
    public List<BdlOdds> getOdds(List<Long> gameIds) {
        if (gameIds == null || gameIds.isEmpty()) {
            return List.of();
        }
        ListResponse<BdlOdds> response = restClient.get()
                .uri(uri -> uri.path("/mlb/v1/odds")
                        .queryParam("game_ids[]", gameIds.toArray())
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

    /** 선수 마스터 일괄 조회. 이름 NULL 스텁 선수 보강에 쓴다. 호출자가 100개 이하로 청크한다. */
    public List<BdlPlayer> getPlayers(List<Long> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return List.of();
        }
        ListResponse<BdlPlayer> response = restClient.get()
                .uri(uri -> uri.path("/mlb/v1/players")
                        .queryParam("player_ids[]", playerIds.toArray())
                        .queryParam("per_page", PER_PAGE)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return response == null || response.data() == null ? List.of() : response.data();
    }


    /**
     * 관심 선수 설정 화면에서 선수 영문 이름을 검색합니다.
     *
     * 기존 getPlayers()는 이미 알고 있는 선수 ID로 상세 정보를
     * 보강할 때 사용하고, 이 메서드는 사용자가 입력한 이름으로
     * 선수를 찾을 때 사용합니다.
     *
     * @param search  선수 영문 이름 검색어
     * @param perPage 한 번에 받을 최대 검색 결과 수
     * @return balldontlie에서 조회한 선수 목록
     */
    @Override
    public List<BdlPlayer> searchPlayers(
            String search,
            int perPage
    ) {
        /*
         * null을 그대로 외부 API에 전달하지 않도록
         * 빈 문자열로 변환하고 앞뒤 공백을 제거합니다.
         */
        String keyword = search == null
                ? ""
                : search.trim();

        /*
         * 빈 검색어로 외부 API 전체 선수를 조회하는 것을 방지합니다.
         */
        if (keyword.isBlank()) {
            return List.of();
        }

        /*
         * balldontlie API의 기존 프로젝트 상한인 100건을 넘지 않게 합니다.
         *
         * 0 이하가 넘어오면 최소 1건,
         * 100을 넘으면 최대 100건으로 제한합니다.
         */
        int normalizedPerPage = Math.max(
                1,
                Math.min(perPage, PER_PAGE)
        );

        ListResponse<BdlPlayer> response = restClient.get()
                .uri(uri -> uri
                        .path("/mlb/v1/players")
                        .queryParam("search", keyword)
                        .queryParam(
                                "per_page",
                                normalizedPerPage
                        )
                        .build()
                )
                .retrieve()
                .body(
                        new ParameterizedTypeReference<>() {
                        }
                );

        return response == null || response.data() == null
                ? List.of()
                : response.data();
    }


    /** 경기의 plate appearance 전체 목록. 이 endpoint는 증분 cursor가 없다. */
    public List<BdlPlateAppearance> getPlateAppearances(long gameId) {
        return getPlateAppearancesRaw(gameId).data();
    }

    /**
     * 경기의 plate appearance 원본 응답과 파싱 결과.
     * 원본은 S3 아카이브 유지(B-7)에 쓰고, 파싱 결과는 runner 상태 매칭에 쓴다.
     */
    public PlateAppearancesRaw getPlateAppearancesRaw(long gameId) {
        JsonNode response = restClient.get()
                .uri(uri -> uri.path("/mlb/v1/plate_appearances")
                        .queryParam("game_id", gameId)
                        .queryParam("per_page", PER_PAGE)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            return new PlateAppearancesRaw(null, List.of());
        }
        JsonNode data = response.path("data");
        List<BdlPlateAppearance> parsed = data.isArray()
                ? objectMapper.convertValue(data, new TypeReference<List<BdlPlateAppearance>>() {
                })
                : List.<BdlPlateAppearance>of();
        return new PlateAppearancesRaw(response, parsed);
    }
}
