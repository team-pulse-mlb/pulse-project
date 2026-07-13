package com.pulse.common.client;

import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.client.BdlDtos.BdlLineup;
import com.pulse.common.client.BdlDtos.BdlOdds;
import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.common.client.BdlDtos.BdlPlayerSeasonStat;
import com.pulse.common.client.BdlDtos.BdlStanding;
import com.pulse.common.client.BdlDtos.ListResponse;
import com.pulse.common.client.BdlDtos.PlateAppearancesRaw;
import java.time.LocalDate;
import java.util.List;

/** 운영 API와 로컬 시뮬레이션이 공유하는 야구 데이터 입력 경계다. */
public interface BaseballDataSource {

    List<BdlGame> getGames(LocalDate date);

    ListResponse<BdlPlay> getPlays(long gameId, Long cursor);

    List<BdlLineup> getLineups(List<Long> gameIds);

    List<BdlOdds> getOdds(List<Long> gameIds);

    List<BdlStanding> getStandings(int season);

    List<BdlPlayerSeasonStat> getPlayerSeasonStats(int season, List<Long> playerIds);

    List<BdlPlayer> getPlayers(List<Long> playerIds);

    List<BdlPlateAppearance> getPlateAppearances(long gameId);

    PlateAppearancesRaw getPlateAppearancesRaw(long gameId);
}
