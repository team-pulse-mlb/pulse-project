package com.pulse.poller;

import com.pulse.common.client.BdlDtos.BdlLineup;
import com.pulse.common.client.BdlDtos.BdlOdds;
import com.pulse.common.client.BdlDtos.BdlPlayerSeasonStat;
import com.pulse.common.client.BdlDtos.BdlStanding;
import com.pulse.common.message.ScoreTaskFactory;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.domain.Game;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PregameTransitionWriter {

    private final PregameGameWriter pregameGameWriter;
    private final ScoreTaskFactory scoreTaskFactory;
    private final ScoreTaskPublisher scoreTaskPublisher;

    /** 경기 전 입력 저장과 변경 경기의 outbox task 발행을 하나의 트랜잭션으로 처리한다. */
    @Transactional
    public PregameWriteOutcome write(PregameWriteRequest request) {
        Set<Long> changedGameIds = new LinkedHashSet<>(request.triggeredGameIds());
        changedGameIds.addAll(pregameGameWriter.upsertLineups(request.lineups(), request.observedAt()));
        changedGameIds.addAll(pregameGameWriter.upsertOdds(
                request.odds(), request.startTimesByGameId(), request.observedAt()));

        StandingsBatch standingsBatch = request.standingsBatch();
        if (standingsBatch != null && pregameGameWriter.upsertStandings(
                standingsBatch.season(),
                standingsBatch.snapshotDate(),
                standingsBatch.standings(),
                request.observedAt()
        )) {
            changedGameIds.addAll(request.gamesById().keySet());
        }

        Set<Long> pitcherIdsToSave = changedGameIds.stream()
                .flatMap(gameId -> request.probablePitcherIdsByGameId()
                        .getOrDefault(gameId, Set.of())
                        .stream())
                .collect(Collectors.toSet());
        List<BdlPlayerSeasonStat> playerSeasonStatsToSave = request.playerSeasonStats().stream()
                .filter(stat -> pitcherIdsToSave.contains(stat.resolvedPlayerId()))
                .toList();
        pregameGameWriter.upsertPlayerSeasonStats(
                request.season(), playerSeasonStatsToSave, request.observedAt());

        Set<Long> publishedGameIds = new LinkedHashSet<>();
        for (Long gameId : changedGameIds) {
            Game game = request.gamesById().get(gameId);
            if (game == null) {
                continue;
            }
            scoreTaskPublisher.publish(scoreTaskFactory.pregameTask(game, request.observedAt()));
            publishedGameIds.add(gameId);
        }
        return new PregameWriteOutcome(Set.copyOf(changedGameIds), Set.copyOf(publishedGameIds));
    }

    public record PregameWriteRequest(
            List<BdlLineup> lineups,
            List<BdlOdds> odds,
            Map<Long, Instant> startTimesByGameId,
            StandingsBatch standingsBatch,
            int season,
            List<BdlPlayerSeasonStat> playerSeasonStats,
            Map<Long, Set<Long>> probablePitcherIdsByGameId,
            Set<Long> triggeredGameIds,
            Map<Long, Game> gamesById,
            Instant observedAt
    ) {
    }

    public record StandingsBatch(int season, LocalDate snapshotDate, List<BdlStanding> standings) {
    }

    public record PregameWriteOutcome(Set<Long> changedGameIds, Set<Long> publishedGameIds) {
    }
}
