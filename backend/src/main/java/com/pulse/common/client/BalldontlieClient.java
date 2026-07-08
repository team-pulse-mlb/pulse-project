package com.pulse.common.client;

import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.client.BdlDtos.BdlPlay;
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
