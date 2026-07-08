package com.pulse.poller;

import com.pulse.common.client.BdlDtos.BdlLineup;
import com.pulse.common.client.BdlDtos.BdlOdds;
import com.pulse.common.client.BdlDtos.BdlPlayerSeasonStat;
import com.pulse.common.client.BdlDtos.BdlStanding;
import com.pulse.domain.Lineup;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.OddsSnapshot;
import com.pulse.domain.OddsSnapshotRepository;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.PlayerSeasonStat;
import com.pulse.domain.PlayerSeasonStatId;
import com.pulse.domain.PlayerSeasonStatRepository;
import com.pulse.domain.Standing;
import com.pulse.domain.StandingRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경기 전 입력(라인업·배당·순위·시즌 스탯) 영속. 변경 여부를 돌려줘
 * PREGAME task 발행 트리거로 쓴다.
 */
@Service
@RequiredArgsConstructor
public class PregameGameWriter {

    private static final String OPERATIONAL_SOURCE = "OPERATIONAL";

    private final LineupRepository lineupRepository;
    private final OddsSnapshotRepository oddsSnapshotRepository;
    private final StandingRepository standingRepository;
    private final PlayerSeasonStatRepository playerSeasonStatRepository;
    private final PlayerRepository playerRepository;

    /** 라인업 upsert. 선발 확정·변경, 타순·포지션 변경이 있는 경기 id를 돌려준다. */
    @Transactional
    public Set<Long> upsertLineups(List<BdlLineup> dtos, Instant observedAt) {
        Set<Long> changedGameIds = new LinkedHashSet<>();
        for (BdlLineup dto : dtos) {
            if (dto.id() == null || dto.gameId() == null || dto.player() == null || dto.player().id() == null
                    || dto.team() == null || dto.team().id() == null) {
                continue;
            }
            upsertPlayer(dto.player(), dto.team().id(), observedAt);

            Lineup lineup = lineupRepository.findByGameIdAndPlayerId(dto.gameId(), dto.player().id())
                    .orElse(null);
            if (lineup != null && !lineup.getId().equals(dto.id())) {
                lineupRepository.delete(lineup);
                lineupRepository.flush();
                lineup = null;
            }
            boolean changed;
            if (lineup == null) {
                lineup = new Lineup();
                lineup.setId(dto.id());
                lineup.setGameId(dto.gameId());
                lineup.setPlayerId(dto.player().id());
                changed = true;
            } else {
                changed = !Objects.equals(lineup.getBattingOrder(), dto.battingOrder())
                        || !Objects.equals(lineup.getPosition(), dto.position())
                        || !Objects.equals(lineup.getIsProbablePitcher(), dto.isProbablePitcher());
            }
            lineup.setTeamId(dto.team().id());
            lineup.setBattingOrder(dto.battingOrder());
            lineup.setPosition(dto.position());
            lineup.setIsProbablePitcher(dto.isProbablePitcher());
            if (changed) {
                lineup.setObservedAt(observedAt);
                lineup.setSource(OPERATIONAL_SOURCE);
                changedGameIds.add(dto.gameId());
            }
            lineupRepository.save(lineup);
        }
        return changedGameIds;
    }

    /**
     * 배당 스냅샷 기록. FIRST_SEEN은 최초 관측만 남기고,
     * PREGAME_FINAL은 시작 전 최신 값으로 덮어쓴다. 기록·갱신된 경기 id를 돌려준다.
     */
    @Transactional
    public Set<Long> upsertOdds(List<BdlOdds> dtos, Map<Long, Instant> startTimesByGameId, Instant observedAt) {
        Set<Long> changedGameIds = new LinkedHashSet<>();
        for (BdlOdds dto : dtos) {
            if (dto.gameId() == null || dto.vendor() == null || dto.vendor().isBlank()) {
                continue;
            }
            Instant startTime = startTimesByGameId.get(dto.gameId());
            if (startTime != null && observedAt.isAfter(startTime)) {
                continue;
            }

            boolean firstSeenInserted = oddsSnapshotRepository
                    .findByGameIdAndVendorAndSnapshotType(dto.gameId(), dto.vendor(), OddsSnapshot.SNAPSHOT_FIRST_SEEN)
                    .isEmpty();
            if (firstSeenInserted) {
                oddsSnapshotRepository.save(newSnapshot(dto, OddsSnapshot.SNAPSHOT_FIRST_SEEN, observedAt));
                changedGameIds.add(dto.gameId());
            }

            OddsSnapshot pregameFinal = oddsSnapshotRepository
                    .findByGameIdAndVendorAndSnapshotType(dto.gameId(), dto.vendor(), OddsSnapshot.SNAPSHOT_PREGAME_FINAL)
                    .orElse(null);
            if (pregameFinal == null) {
                oddsSnapshotRepository.save(newSnapshot(dto, OddsSnapshot.SNAPSHOT_PREGAME_FINAL, observedAt));
                changedGameIds.add(dto.gameId());
            } else if (oddsValuesChanged(pregameFinal, dto)) {
                applyOddsValues(pregameFinal, dto, observedAt);
                oddsSnapshotRepository.save(pregameFinal);
                changedGameIds.add(dto.gameId());
            }
        }
        return changedGameIds;
    }

    /** 순위 일 배치 upsert. 새 스냅샷 행이 추가되면 true. */
    @Transactional
    public boolean upsertStandings(int season, LocalDate snapshotDate, List<BdlStanding> dtos, Instant observedAt) {
        boolean inserted = false;
        for (BdlStanding dto : dtos) {
            if (dto.team() == null || dto.team().id() == null) {
                continue;
            }
            Standing standing = standingRepository
                    .findBySeasonAndSnapshotDateAndTeamId(season, snapshotDate, dto.team().id())
                    .orElse(null);
            if (standing == null) {
                standing = new Standing();
                standing.setSeason(season);
                standing.setSnapshotDate(snapshotDate);
                standing.setTeamId(dto.team().id());
                inserted = true;
            }
            standing.setLeagueName(dto.leagueName());
            standing.setDivisionName(dto.divisionName());
            standing.setWins(dto.wins());
            standing.setLosses(dto.losses());
            standing.setWinPercent(dto.winPercent());
            standing.setGamesBehind(dto.gamesBehind());
            standing.setPlayoffPercent(dto.playoffPercent());
            standing.setWildcardPercent(dto.wildcardPercent());
            standing.setStreak(dto.streak());
            standing.setLastTenGames(dto.lastTenGames());
            standing.setObservedAt(observedAt);
            standing.setSource(OPERATIONAL_SOURCE);
            standingRepository.save(standing);
        }
        return inserted;
    }

    /** 선수 시즌 스탯 캐시 최신 덮어쓰기. */
    @Transactional
    public void upsertPlayerSeasonStats(int season, List<BdlPlayerSeasonStat> dtos, Instant now) {
        for (BdlPlayerSeasonStat dto : dtos) {
            Long playerId = dto.resolvedPlayerId();
            if (playerId == null) {
                continue;
            }
            ensurePlayer(playerId, now);
            PlayerSeasonStat stat = playerSeasonStatRepository
                    .findById(new PlayerSeasonStatId(season, playerId))
                    .orElseGet(() -> {
                        PlayerSeasonStat created = new PlayerSeasonStat();
                        created.setSeason(season);
                        created.setPlayerId(playerId);
                        return created;
                    });
            stat.setPitchingEra(dto.pitchingEra());
            stat.setPitchingWar(dto.pitchingWar());
            stat.setPitchingWhip(dto.pitchingWhip());
            stat.setPitchingKPer9(dto.pitchingKPer9());
            stat.setBattingWar(dto.battingWar());
            stat.setBattingOps(dto.battingOps());
            stat.setBattingHr(dto.battingHr());
            stat.setUpdatedAt(now);
            playerSeasonStatRepository.save(stat);
        }
    }

    private void upsertPlayer(BdlLineup.Player dto, Long teamId, Instant observedAt) {
        Player player = playerRepository.findById(dto.id()).orElseGet(() -> {
            Player created = new Player();
            created.setId(dto.id());
            created.setCreatedAt(observedAt);
            return created;
        });
        if (dto.fullName() != null) {
            player.setFullName(dto.fullName());
        }
        if (dto.firstName() != null) {
            player.setFirstName(dto.firstName());
        }
        if (dto.lastName() != null) {
            player.setLastName(dto.lastName());
        }
        if (dto.position() != null) {
            player.setPosition(dto.position());
        }
        player.setTeamId(teamId);
        player.setUpdatedAt(observedAt);
        playerRepository.save(player);
    }

    private void ensurePlayer(Long playerId, Instant now) {
        if (playerRepository.existsById(playerId)) {
            return;
        }
        Player player = new Player();
        player.setId(playerId);
        player.setCreatedAt(now);
        player.setUpdatedAt(now);
        playerRepository.save(player);
    }

    private static OddsSnapshot newSnapshot(BdlOdds dto, String snapshotType, Instant observedAt) {
        OddsSnapshot snapshot = new OddsSnapshot();
        snapshot.setGameId(dto.gameId());
        snapshot.setVendor(dto.vendor());
        snapshot.setSnapshotType(snapshotType);
        applyOddsValues(snapshot, dto, observedAt);
        return snapshot;
    }

    private static void applyOddsValues(OddsSnapshot snapshot, BdlOdds dto, Instant observedAt) {
        snapshot.setMoneylineHomeOdds(dto.moneylineHomeOdds());
        snapshot.setMoneylineAwayOdds(dto.moneylineAwayOdds());
        snapshot.setSpreadHomeValue(dto.spreadHomeValue());
        snapshot.setSpreadAwayValue(dto.spreadAwayValue());
        snapshot.setSpreadHomeOdds(dto.spreadHomeOdds());
        snapshot.setSpreadAwayOdds(dto.spreadAwayOdds());
        snapshot.setTotalValue(dto.totalValue());
        snapshot.setTotalOverOdds(dto.totalOverOdds());
        snapshot.setTotalUnderOdds(dto.totalUnderOdds());
        snapshot.setVendorUpdatedAt(parseInstant(dto.vendorUpdatedAt()));
        snapshot.setObservedAt(observedAt);
        snapshot.setSource(OPERATIONAL_SOURCE);
    }

    private static boolean oddsValuesChanged(OddsSnapshot snapshot, BdlOdds dto) {
        return !Objects.equals(snapshot.getMoneylineHomeOdds(), dto.moneylineHomeOdds())
                || !Objects.equals(snapshot.getMoneylineAwayOdds(), dto.moneylineAwayOdds())
                || compareTo(snapshot.getSpreadHomeValue(), dto.spreadHomeValue())
                || compareTo(snapshot.getSpreadAwayValue(), dto.spreadAwayValue())
                || compareTo(snapshot.getTotalValue(), dto.totalValue());
    }

    private static boolean compareTo(java.math.BigDecimal left, java.math.BigDecimal right) {
        if (left == null || right == null) {
            return !Objects.equals(left, right);
        }
        return left.compareTo(right) != 0;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
