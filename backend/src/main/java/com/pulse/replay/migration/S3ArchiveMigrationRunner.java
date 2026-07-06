package com.pulse.replay.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulse.replay.migration.MigrationJdbcWriter.UpsertResult;
import com.pulse.replay.migration.MigrationRows.LineupRow;
import com.pulse.replay.migration.MigrationRows.OddsObservation;
import com.pulse.replay.migration.MigrationRows.PlateAppearanceRow;
import com.pulse.replay.migration.MigrationRows.PlayRow;
import com.pulse.replay.migration.MigrationRows.PlayerRow;
import com.pulse.replay.migration.MigrationRows.StandingRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("migration")
@RequiredArgsConstructor
@Slf4j
class S3ArchiveMigrationRunner implements ApplicationRunner {

    private final MigrationRawArchiveClient archiveClient;
    private final MigrationJsonMapper jsonMapper;
    private final MigrationJdbcWriter writer;
    private final ConfigurableApplicationContext applicationContext;
    private final PlateAppearancePlayMatcher plateAppearancePlayMatcher = new PlateAppearancePlayMatcher();
    private final OddsSnapshotSelector oddsSnapshotSelector = new OddsSnapshotSelector();

    @Override
    public void run(ApplicationArguments args) {
        MigrationStats stats = new MigrationStats();
        Map<String, List<MigrationEnvelope>> envelopes = loadEnvelopes(stats);

        ingestTeams(envelopes, stats);
        ingestGames(envelopes.get("raw/games/"), stats);
        ingestPlayers(envelopes.get("raw/lineups/"), stats);
        ingestPlays(envelopes.get("raw/plays/"), false, "S3_LIVE_ARCHIVE", stats);
        ingestPlays(envelopes.get("raw/backfill/plays/"), true, "S3_BACKFILL", stats);
        updateRunnerState(envelopes, stats);
        ingestOdds(envelopes.get("raw/odds/"), stats);
        ingestStandings(envelopes.get("raw/standings/"), stats);
        ingestLineups(envelopes.get("raw/lineups/"), stats);

        logSummary(stats);
        applicationContext.close();
    }

    private Map<String, List<MigrationEnvelope>> loadEnvelopes(MigrationStats stats) {
        Map<String, List<MigrationEnvelope>> byPrefix = new LinkedHashMap<>();
        for (String prefix : MigrationRawArchiveClient.PREFIXES) {
            List<MigrationEnvelope> loaded = archiveClient.loadPrefix(prefix);
            byPrefix.put(prefix, loaded);
            stats.prefixObjects(prefix, loaded.size());
        }
        return byPrefix;
    }

    private void ingestTeams(Map<String, List<MigrationEnvelope>> envelopes, MigrationStats stats) {
        for (MigrationEnvelope envelope : envelopes.getOrDefault("raw/games/", List.of())) {
            jsonMapper.teamsFromGames(envelope).forEach(row -> count("teams", writer.insertTeam(row), stats));
        }
        for (MigrationEnvelope envelope : envelopes.getOrDefault("raw/standings/", List.of())) {
            jsonMapper.teamsFromNode(envelope).forEach(row -> count("teams", writer.insertTeam(row), stats));
        }
        for (MigrationEnvelope envelope : envelopes.getOrDefault("raw/lineups/", List.of())) {
            jsonMapper.teamsFromNode(envelope).forEach(row -> count("teams", writer.insertTeam(row), stats));
        }
    }

    private void ingestGames(List<MigrationEnvelope> envelopes, MigrationStats stats) {
        for (MigrationEnvelope envelope : safe(envelopes)) {
            for (JsonNode node : jsonMapper.dataNodes(envelope.response())) {
                var row = jsonMapper.game(envelope, node);
                if (row == null) {
                    stats.skipped("games");
                    continue;
                }
                count("games", writer.upsertGame(row), stats);
            }
        }
    }

    private void ingestPlayers(List<MigrationEnvelope> envelopes, MigrationStats stats) {
        for (MigrationEnvelope envelope : safe(envelopes)) {
            for (JsonNode node : jsonMapper.dataNodes(envelope.response())) {
                PlayerRow row = jsonMapper.player(node);
                if (row == null) {
                    stats.skipped("players");
                    continue;
                }
                count("players", writer.insertPlayer(row), stats);
            }
        }
    }

    private void ingestPlays(List<MigrationEnvelope> envelopes, boolean backfilled, String source, MigrationStats stats) {
        for (MigrationEnvelope envelope : safe(envelopes)) {
            for (JsonNode node : jsonMapper.dataNodes(envelope.response())) {
                PlayRow row = jsonMapper.play(envelope, node, backfilled, source);
                if (row == null) {
                    stats.skipped("plays");
                    continue;
                }
                if (!writer.gameExists(row.gameId())) {
                    stats.skipped("plays");
                    continue;
                }
                insertMinimalPlayer(row.batterId(), stats);
                insertMinimalPlayer(row.pitcherId(), stats);
                count("plays", writer.insertPlay(row), stats);
            }
        }
    }

    private void updateRunnerState(Map<String, List<MigrationEnvelope>> envelopes, MigrationStats stats) {
        Map<Long, Map<Long, PlateAppearanceRow>> latestByGameAndNumber = new HashMap<>();
        List<MigrationEnvelope> paEnvelopes = new ArrayList<>();
        paEnvelopes.addAll(envelopes.getOrDefault("raw/plate_appearances/", List.of()));
        paEnvelopes.addAll(envelopes.getOrDefault("raw/backfill/plate_appearances/", List.of()));
        paEnvelopes.sort(Comparator.comparing(MigrationEnvelope::observedAt).thenComparing(MigrationEnvelope::key));

        for (MigrationEnvelope envelope : paEnvelopes) {
            for (JsonNode node : jsonMapper.dataNodes(envelope.response())) {
                PlateAppearanceRow row = jsonMapper.plateAppearance(envelope, node);
                if (row == null) {
                    stats.skipped("plays");
                    continue;
                }
                insertMinimalPlayer(row.batterId(), stats);
                insertMinimalPlayer(row.pitcherId(), stats);
                latestByGameAndNumber
                        .computeIfAbsent(row.gameId(), ignored -> new HashMap<>())
                        .merge(row.paNumber(), row, this::laterPlateAppearance);
            }
        }

        for (Map.Entry<Long, Map<Long, PlateAppearanceRow>> entry : latestByGameAndNumber.entrySet()) {
            Long gameId = entry.getKey();
            List<PlateAppearanceRow> paRows = entry.getValue().values().stream()
                    .sorted(Comparator.comparing(PlateAppearanceRow::paNumber))
                    .toList();
            var result = plateAppearancePlayMatcher.match(writer.playsForGame(gameId), paRows);
            writer.updateRunners(result.updates());
            stats.updated("plays", result.updates().size());
            stats.skipped("plays", result.unmatchedGroups() + result.unmatchedPlateAppearances());
        }
    }

    private void ingestOdds(List<MigrationEnvelope> envelopes, MigrationStats stats) {
        Map<OddsKey, List<OddsObservation>> observations = new HashMap<>();
        for (MigrationEnvelope envelope : safe(envelopes)) {
            for (JsonNode node : jsonMapper.dataNodes(envelope.response())) {
                OddsObservation row = jsonMapper.odds(envelope, node);
                if (row == null) {
                    stats.skipped("odds_snapshots");
                    continue;
                }
                observations.computeIfAbsent(new OddsKey(row.gameId(), row.vendor()), ignored -> new ArrayList<>()).add(row);
            }
        }

        for (Map.Entry<OddsKey, List<OddsObservation>> entry : observations.entrySet()) {
            Instant startTime = writer.gameStartTime(entry.getKey().gameId());
            if (startTime == null) {
                stats.skipped("odds_snapshots", entry.getValue().size());
                continue;
            }
            var snapshots = oddsSnapshotSelector.select(entry.getValue(), startTime);
            if (snapshots.isEmpty()) {
                stats.skipped("odds_snapshots", entry.getValue().size());
                continue;
            }
            long pregameCount = entry.getValue().stream()
                    .filter(observation -> observation.pregameTime() != null)
                    .filter(observation -> !observation.pregameTime().isAfter(startTime))
                    .count();
            stats.skipped("odds_snapshots", (int) (entry.getValue().size() - pregameCount));
            snapshots.forEach(row -> count("odds_snapshots", writer.insertOddsSnapshot(row), stats));
        }
    }

    private void ingestStandings(List<MigrationEnvelope> envelopes, MigrationStats stats) {
        for (MigrationEnvelope envelope : safe(envelopes)) {
            for (JsonNode node : jsonMapper.dataNodes(envelope.response())) {
                StandingRow row = jsonMapper.standing(envelope, node);
                if (row == null) {
                    stats.skipped("standings");
                    continue;
                }
                count("standings", writer.insertStanding(row), stats);
            }
        }
    }

    private void ingestLineups(List<MigrationEnvelope> envelopes, MigrationStats stats) {
        for (MigrationEnvelope envelope : safe(envelopes)) {
            for (JsonNode node : jsonMapper.dataNodes(envelope.response())) {
                LineupRow row = jsonMapper.lineup(envelope, node);
                if (row == null || !writer.gameExists(row.gameId())) {
                    stats.skipped("lineups");
                    continue;
                }
                count("lineups", writer.upsertLineup(row), stats);
            }
        }
    }

    private PlateAppearanceRow laterPlateAppearance(PlateAppearanceRow previous, PlateAppearanceRow current) {
        return current.observedAt().compareTo(previous.observedAt()) >= 0 ? current : previous;
    }

    private void insertMinimalPlayer(Long playerId, MigrationStats stats) {
        if (playerId != null) {
            count("players", writer.insertMinimalPlayer(playerId), stats);
        }
    }

    private void count(String table, UpsertResult result, MigrationStats stats) {
        switch (result) {
            case INSERTED -> stats.inserted(table);
            case UPDATED -> stats.updated(table);
            case SKIPPED -> stats.skipped(table);
        }
    }

    private void logSummary(MigrationStats stats) {
        log.info("S3 이전 배치 완료");
        stats.prefixObjectCounts().forEach((prefix, count) ->
                log.info("프리픽스별 읽은 객체 수: prefix={}, objects={}", prefix, count));
        stats.tableCounts().forEach((table, counts) ->
                log.info("테이블별 처리 결과: table={}, inserted={}, updated={}, skipped={}",
                        table, counts.inserted, counts.updated, counts.skipped));
    }

    private static List<MigrationEnvelope> safe(List<MigrationEnvelope> envelopes) {
        return envelopes == null ? List.of() : envelopes;
    }

    private record OddsKey(Long gameId, String vendor) {
    }
}
