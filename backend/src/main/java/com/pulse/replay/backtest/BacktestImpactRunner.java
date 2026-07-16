package com.pulse.replay.backtest;

import static com.pulse.replay.backtest.BacktestModels.Cycle;
import static com.pulse.replay.backtest.BacktestModels.GameData;
import static com.pulse.replay.backtest.BacktestModels.ReplayResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.config.ScoringProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("backtest")
@EnableConfigurationProperties(BacktestProperties.class)
@RequiredArgsConstructor
@Slf4j
public class BacktestImpactRunner implements ApplicationRunner {
    private final BacktestProperties options;
    private final BacktestJdbcRepository repository;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments arguments) {
        validate();
        ScoringConstantsLoader loader = new ScoringConstantsLoader();
        ScoringProperties baselineProperties = loader.loadBaseline(options.baseline());
        ScoringProperties candidateProperties = loader.loadCandidate(options.candidate());
        List<GameData> loadedGames = repository.load(options);
        List<GameData> games = loadedGames.stream()
                .filter(game -> !game.plays().isEmpty())
                .toList();
        requireGames(games);
        int excludedGames = loadedGames.size() - games.size();
        log.info("play가 없어 백테스트에서 제외된 경기: {}", excludedGames);
        GameReplayEngine engine = new GameReplayEngine(options, objectMapper);
        AlertSimulator alerts = new AlertSimulator();
        List<ReplayResult> baseline = replay(games, baselineProperties, engine, alerts);
        List<ReplayResult> candidate = replay(games, candidateProperties, engine, alerts);
        ImpactReportGenerator.Paths paths = new ImpactReportGenerator(objectMapper).write(
                options, baselineProperties, candidateProperties, baseline, candidate);
        log.info("가중치 백테스트 완료: games={}, baseline=v{}, candidate=v{}, json={}, markdown={}",
                games.size(), baselineProperties.version(), candidateProperties.version(), paths.json(), paths.markdown());
        context.close();
    }

    private List<ReplayResult> replay(List<GameData> games, ScoringProperties properties,
                                      GameReplayEngine engine, AlertSimulator alerts) {
        Map<Long, List<Cycle>> cyclesByGame = new LinkedHashMap<>();
        games.forEach(game -> cyclesByGame.put(game.game().gameId(), engine.replay(game, properties)));
        Map<Long, Integer> alertCounts = alerts.simulate(cyclesByGame, properties);
        return games.stream()
                .map(game -> new ReplayResult(
                        game,
                        cyclesByGame.get(game.game().gameId()),
                        alertCounts.getOrDefault(game.game().gameId(), 0)))
                .toList();
    }

    static void requireGames(List<GameData> games) {
        if (games.isEmpty()) {
            throw new IllegalStateException("백테스트 기간에 play가 있는 경기가 없습니다.");
        }
    }

    private void validate() {
        if (options.baseline() == null || options.baseline().isBlank()) {
            throw new IllegalArgumentException("baseline은 필수입니다.");
        }
        if (options.from() == null || options.to() == null) {
            throw new IllegalArgumentException("from과 to는 필수입니다.");
        }
        if (options.from().isAfter(options.to())) {
            throw new IllegalArgumentException("from은 to보다 늦을 수 없습니다.");
        }
        List<String> allowed = List.of("OPERATIONAL", "S3_LIVE_ARCHIVE", "S3_BACKFILL");
        if (!allowed.containsAll(options.sources())) {
            throw new IllegalArgumentException("지원하지 않는 plays.source가 포함되어 있습니다: " + options.sources());
        }
    }
}
