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

    /**
     * 관심 선수 설정 화면에서 선수 영문 이름을 검색합니다.
     *
     * 운영 환경에서는 balldontlie MLB 선수 검색 API를 호출하고,
     * 시뮬레이션 환경에서는 빈 목록을 반환합니다.
     */
    List<BdlPlayer> searchPlayers(String search, int perPage);

    List<BdlPlateAppearance> getPlateAppearances(long gameId);

    PlateAppearancesRaw getPlateAppearancesRaw(long gameId);
}
