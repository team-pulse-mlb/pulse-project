package com.pulse.common.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class BalldontlieClientTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private MockRestServiceServer server;
    private BalldontlieClient client;

    @BeforeEach
    void setUp() {
        Metrics.addRegistry(meterRegistry);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BalldontlieClient(
                new BdlProperties("https://api.example.test", "test-key", null, null),
                new ObjectMapper(),
                builder
        );
    }

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(meterRegistry);
        Metrics.globalRegistry.clear();
        meterRegistry.close();
    }

    @Test
    void 여러_날짜를_한_요청으로_보내고_남은_호출_예산을_기록한다() {
        List<LocalDate> dates = List.of(
                LocalDate.of(2026, 7, 7),
                LocalDate.of(2026, 7, 8),
                LocalDate.of(2026, 7, 9),
                LocalDate.of(2026, 7, 10)
        );
        server.expect(request -> {
                    // dates[] 키는 URL 인코딩(dates%5B%5D)될 수 있으므로 디코딩해서 비교한다.
                    var query = UriComponentsBuilder.fromUri(request.getURI()).build(true).getQueryParams();
                    var dateValues = query.entrySet().stream()
                            .filter(entry -> URLDecoder.decode(entry.getKey(), StandardCharsets.UTF_8).equals("dates[]"))
                            .findFirst()
                            .map(java.util.Map.Entry::getValue)
                            .orElse(List.of());
                    assertThat(dateValues).containsExactly(
                            "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10"
                    );
                    assertThat(query.getFirst("per_page")).isEqualTo("100");
                })
                .andRespond(withSuccess("{\"data\":[]}", APPLICATION_JSON)
                        .header("x-ratelimit-remaining", "593"));

        assertThat(client.getGames(dates)).isEmpty();

        server.verify();
        assertThat(meterRegistry.get("pulse.bdl.rate.limit.remaining")
                .tag("endpoint", "/mlb/v1/games")
                .gauge()
                .value()).isEqualTo(593.0);
    }

    @Test
    void 호출_예산_헤더가_없으면_지표를_생략한다() {
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/mlb/v1/games"))
                .andRespond(withSuccess("{\"data\":[]}", APPLICATION_JSON));

        assertThat(client.getGames(LocalDate.of(2026, 7, 8))).isEmpty();

        server.verify();
    }
}
