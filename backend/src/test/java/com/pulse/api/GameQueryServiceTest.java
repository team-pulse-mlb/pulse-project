package com.pulse.api;

import com.pulse.api.GameQueryService.*;
import com.pulse.domain.*;
import com.pulse.scorer.TensionCurveQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GameQueryServiceTest {

    private final GameRepository gameRepository =
            mock(GameRepository.class);

    private final TeamRepository teamRepository =
            mock(TeamRepository.class);

    private final PlayRepository playRepository =
            mock(PlayRepository.class);

    private final PlayerRepository playerRepository =
            mock(PlayerRepository.class);

    private final LineupRepository lineupRepository =
            mock(LineupRepository.class);

    private final TensionCurveQueryService tensionCurveQueryService =
            mock(TensionCurveQueryService.class);

    private final GameQueryService service =
            new GameQueryService(
                    gameRepository,
                    teamRepository,
                    playRepository,
                    playerRepository,
                    lineupRepository,
                    tensionCurveQueryService);

    @Test
    void scheduledGame_shouldReturnScheduledDetailEvenWhenRevealedIsRequested() {
        // given
        Game game = scheduledGame(100L);

        Lineup homePitcher =
                probablePitcher(
                        100L,
                        101L,
                        1L);

        Lineup awayPitcher =
                probablePitcher(
                        100L,
                        202L,
                        2L);

        Player homePlayer =
                player(
                        101L,
                        "Home Starter");

        Player awayPlayer =
                player(
                        202L,
                        "Away Starter");

        when(gameRepository.findById(100L))
                .thenReturn(Optional.of(game));

        when(
                lineupRepository
                        .findByGameIdAndIsProbablePitcherTrue(100L))
                .thenReturn(
                        List.of(
                                homePitcher,
                                awayPitcher));

        when(
                playerRepository.findAllById(
                        List.of(101L, 202L)))
                .thenReturn(
                        List.of(
                                homePlayer,
                                awayPlayer));

        // when
        GameDetailView response =
                service.getGameDetail(
                        100L,
                        "revealed");

        // then
        assertThat(response)
                .isInstanceOfSatisfying(
                        ScheduledGameDetailResponse.class,
                        detail -> {
                            /*
                             * 예정 경기는 공개할 결과가 없으므로
                             * revealed 요청에도 보호 상태를 반환한다.
                             */
                            assertThat(detail.displayMode())
                                    .isEqualTo(DisplayMode.PROTECTED);

                            assertThat(detail.status())
                                    .isEqualTo(Game.STATUS_SCHEDULED);

                            assertThat(
                                    detail.probablePitchers().home())
                                    .isEqualTo("Home Starter");

                            assertThat(
                                    detail.probablePitchers().away())
                                    .isEqualTo("Away Starter");
                        });

        /*
         * 예정 경기에서는 최근 play를 조회하지 않는다.
         */
        verifyNoInteractions(playRepository);
    }

    @Test
    void finalGame_shouldReturnFinalDetailWithoutUsingLatestPlayAsSituation() {
        // given
        Game game = finalGame(200L);
        Play incompleteScoringPlay = scoringPlay();

        when(gameRepository.findById(200L))
                .thenReturn(Optional.of(game));

        when(
                playRepository
                        .findByGameIdOrderByPlayOrderAsc(200L))
                .thenReturn(
                        List.of(incompleteScoringPlay));

        when(
                tensionCurveQueryService
                        .getRevealedCurve(200L))
                .thenReturn(
                        List.of(
                                new TensionCurveQueryService.RevealedPoint(
                                        1,
                                        "Top",
                                        2),
                                new TensionCurveQueryService.RevealedPoint(
                                        2,
                                        "Bottom",
                                        4)));

        // when
        GameDetailView response =
                service.getGameDetail(
                        200L,
                        "revealed");

        // then
        assertThat(response)
                .isInstanceOfSatisfying(
                        RevealedFinalGameDetailResponse.class,
                        detail -> {
                            assertThat(detail.displayMode())
                                    .isEqualTo(DisplayMode.REVEALED);

                            assertThat(detail.finalScore().home())
                                    .isEqualTo(10);

                            assertThat(detail.finalScore().away())
                                    .isEqualTo(4);

                            assertThat(detail.inningScores().home())
                                    .containsExactly(
                                            0,
                                            3,
                                            0,
                                            2,
                                            0,
                                            1,
                                            3,
                                            1);

                            assertThat(detail.scoringSummary())
                                    .singleElement()
                                    .satisfies(
                                            scoringPlay -> {
                                                assertThat(
                                                        scoringPlay.inning())
                                                        .isEqualTo(2);

                                                assertThat(
                                                        scoringPlay.inningType())
                                                        .isEqualTo("Bottom");
                                            });

                            assertThat(detail.tensionCurve())
                                    .containsExactly(
                                            new RevealedTensionPointResponse(
                                                    1,
                                                    "Top",
                                                    2),
                                            new RevealedTensionPointResponse(
                                                    2,
                                                    "Bottom",
                                                    4));
                        });

        /*
         * 종료 경기에서는 오래된 마지막 play를 현재 상황으로
         * 해석하는 내림차순 조회를 실행하지 않는다.
         */
        verify(
                playRepository,
                never())
                .findByGameIdOrderByPlayOrderDesc(
                        eq(200L),
                        any(Pageable.class));
    }

    @Test
    void invalidMode_shouldFallBackToProtectedLiveDetail() {
        // given
        Game game = liveGame(300L);
        Play latestPlay = livePlay();

        when(gameRepository.findById(300L))
                .thenReturn(Optional.of(game));

        when(
                playRepository
                        .findByGameIdOrderByPlayOrderDesc(
                                eq(300L),
                                any(Pageable.class)))
                .thenReturn(List.of(latestPlay));

        // when
        GameDetailView response =
                service.getGameDetail(
                        300L,
                        "invalid-mode");

        // then
        assertThat(response)
                .isInstanceOfSatisfying(
                        ProtectedGameDetailResponse.class,
                        detail -> {
                            /*
                             * 잘못된 mode가 공개 응답을 만드는 일을 막는다.
                             */
                            assertThat(detail.displayMode())
                                    .isEqualTo(DisplayMode.PROTECTED);

                            assertThat(detail.inning())
                                    .isEqualTo(8);

                            assertThat(detail.periodLabel())
                                    .isEqualTo("후반");

                            assertThat(detail.situation().outs())
                                    .isEqualTo(2);

                            assertThat(
                                    detail.situation().scoringPosition())
                                    .isTrue();
                        });
    }

    @Test
    void revealedLiveGame_shouldReturnVenue() {
        // given
        Game game = liveGame(301L);
        Play latestPlay = livePlay();

        latestPlay.setGameId(301L);

        when(gameRepository.findById(301L))
                .thenReturn(Optional.of(game));

        when(
                playRepository
                        .findByGameIdOrderByPlayOrderDesc(
                                eq(301L),
                                any(Pageable.class)))
                .thenReturn(List.of(latestPlay));

        // when
        GameDetailView response =
                service.getGameDetail(
                        301L,
                        "revealed");

        // then
        assertThat(response)
                .isInstanceOfSatisfying(
                        RevealedGameDetailResponse.class,
                        detail -> {
                            assertThat(detail.displayMode())
                                    .isEqualTo(DisplayMode.REVEALED);

                            /*
                             * Game의 구장이 공개 LIVE DTO까지
                             * 연결되는지 검증한다.
                             */
                            assertThat(detail.venue())
                                    .isEqualTo("Wrigley Field");
                        });
    }

    @Test
    void scheduledGame_shouldReturnStartingLineupsForBothTeams() {
        // given
        Game game = scheduledGame(401L);

        Lineup awaySecond =
                startingHitter(
                        401L,
                        212L,
                        2L,
                        2,
                        "CF");

        Lineup homeFirst =
                startingHitter(
                        401L,
                        111L,
                        1L,
                        1,
                        "2B");

        Lineup awayFirst =
                startingHitter(
                        401L,
                        211L,
                        2L,
                        1,
                        "SS");

        when(gameRepository.findById(401L))
                .thenReturn(Optional.of(game));

        when(
                lineupRepository
                        .findByGameIdAndIsProbablePitcherTrue(
                                401L))
                .thenReturn(List.of());

        when(
                lineupRepository.findByGameId(401L))
                .thenReturn(
                        List.of(
                                awaySecond,
                                homeFirst,
                                awayFirst));

        when(
                playerRepository.findAllById(
                        List.of(
                                212L,
                                111L,
                                211L)))
                .thenReturn(
                        List.of(
                                player(
                                        212L,
                                        "Away Second"),
                                player(
                                        111L,
                                        "Home First"),
                                player(
                                        211L,
                                        "Away First")));

        // when
        GameDetailView response =
                service.getGameDetail(
                        401L,
                        null);

        // then
        assertThat(response)
                .isInstanceOfSatisfying(
                        ScheduledGameDetailResponse.class,
                        detail -> {
                            assertThat(
                                    detail.startingLineups()
                                            .home())
                                    .hasSize(1);

                            assertThat(
                                    detail.startingLineups()
                                            .home()
                                            .getFirst()
                                            .playerName())
                                    .isEqualTo(
                                            "Home First");

                            assertThat(
                                    detail.startingLineups()
                                            .away())
                                    .hasSize(2);

                            assertThat(
                                    detail.startingLineups()
                                            .away()
                                            .get(0)
                                            .playerName())
                                    .isEqualTo(
                                            "Away First");

                            assertThat(
                                    detail.startingLineups()
                                            .away()
                                            .get(1)
                                            .playerName())
                                    .isEqualTo(
                                            "Away Second");
                        });
    }

    @Test
    void scheduledGame_shouldReturnNullPitchersWhenLineupsAreMissing() {
        // given
        Game game = scheduledGame(400L);

        when(gameRepository.findById(400L))
                .thenReturn(Optional.of(game));

        when(
                lineupRepository
                        .findByGameIdAndIsProbablePitcherTrue(400L))
                .thenReturn(List.of());

        // when
        GameDetailView response =
                service.getGameDetail(
                        400L,
                        null);

        // then
        assertThat(response)
                .isInstanceOfSatisfying(
                        ScheduledGameDetailResponse.class,
                        detail -> {
                            assertThat(
                                    detail.probablePitchers().home())
                                    .isNull();

                            assertThat(
                                    detail.probablePitchers().away())
                                    .isNull();
                        });

        /*
         * 선발 라인업이 없으면 선수 테이블까지 조회하지 않는다.
         */
        verifyNoInteractions(playerRepository);
    }


    @Test
    void protectedFinalGame_shouldUseProtectedHeadline() {
        // given
        Game game = finalGame(500L);

        game.setFinalHeadlineProtected(
                "스포일러 없이 긴장감이 이어진 경기");

        game.setFinalHeadlineRevealed(
                "홈팀이 10-4로 승리한 경기");

        when(gameRepository.findById(500L))
                .thenReturn(Optional.of(game));

        // when
        GameDetailView response =
                service.getGameDetail(
                        500L,
                        "protected");

        // then
        assertThat(response)
                .isInstanceOfSatisfying(
                        ProtectedFinalGameDetailResponse.class,
                        detail -> {
                            assertThat(detail.headline())
                                    .isEqualTo(
                                            "스포일러 없이 긴장감이 이어진 경기");

                            assertThat(detail.displayMode())
                                    .isEqualTo(
                                            DisplayMode.PROTECTED);
                        });
    }

    @Test
    void revealedFinalGame_shouldUseRevealedHeadline() {
        // given
        Game game = finalGame(501L);

        game.setFinalHeadlineProtected(
                "스포일러 없이 긴장감이 이어진 경기");

        game.setFinalHeadlineRevealed(
                "홈팀이 10-4로 승리한 경기");

        when(gameRepository.findById(501L))
                .thenReturn(Optional.of(game));

        /*
         * 종료 공개 응답은 득점 요약을 만들기 위해
         * 오름차순 play 목록을 조회한다.
         */
        when(
                playRepository
                        .findByGameIdOrderByPlayOrderAsc(501L))
                .thenReturn(List.of());

        // when
        GameDetailView response =
                service.getGameDetail(
                        501L,
                        "revealed");

        // then
        assertThat(response)
                .isInstanceOfSatisfying(
                        RevealedFinalGameDetailResponse.class,
                        detail -> {
                            assertThat(detail.headline())
                                    .isEqualTo(
                                            "홈팀이 10-4로 승리한 경기");

                            assertThat(detail.displayMode())
                                    .isEqualTo(
                                            DisplayMode.REVEALED);
                        });
    }

    @Test
    void finalGame_shouldReturnNullHeadlineWhenStoredHeadlineIsBlank() {
        // given
        Game game = finalGame(502L);

        game.setFinalHeadlineProtected("   ");
        game.setFinalHeadlineRevealed("   ");

        when(gameRepository.findById(502L))
                .thenReturn(Optional.of(game));

        when(
                playRepository
                        .findByGameIdOrderByPlayOrderAsc(502L))
                .thenReturn(List.of());

        // when
        GameDetailView protectedResponse =
                service.getGameDetail(
                        502L,
                        "protected");

        GameDetailView revealedResponse =
                service.getGameDetail(
                        502L,
                        "revealed");

        // then
        assertThat(protectedResponse)
                .isInstanceOfSatisfying(
                        ProtectedFinalGameDetailResponse.class,
                        detail ->
                                assertThat(detail.headline())
                                        .isNull());

        assertThat(revealedResponse)
                .isInstanceOfSatisfying(
                        RevealedFinalGameDetailResponse.class,
                        detail ->
                                assertThat(detail.headline())
                                        .isNull());
    }

    private static Game scheduledGame(long gameId) {
        Game game = baseGame(gameId);

        game.setStatus(Game.STATUS_SCHEDULED);
        game.setStartTime(
                Instant.parse("2026-07-15T00:05:00Z"));
        game.setVenue("Test Ballpark");

        return game;
    }

    private static Game finalGame(long gameId) {
        Game game = baseGame(gameId);

        game.setStatus(Game.STATUS_FINAL);
        game.setStartTime(
                Instant.parse("2026-07-03T00:05:00Z"));

        game.setPeriod(9);
        game.setHomeRuns(10);
        game.setAwayRuns(4);

        game.setHomeInningScores(
                List.of(
                        0,
                        3,
                        0,
                        2,
                        0,
                        1,
                        3,
                        1));

        game.setAwayInningScores(
                List.of(
                        0,
                        0,
                        0,
                        0,
                        3,
                        0,
                        0,
                        1,
                        0));

        /*
         * 빈 문자열은 서비스에서 null 헤드라인으로 변환된다.
         */
        game.setFinalHeadlineProtected("");
        game.setFinalHeadlineRevealed("");

        return game;
    }

    private static Game liveGame(long gameId) {
        Game game = baseGame(gameId);

        game.setStatus(Game.STATUS_IN_PROGRESS);
        game.setStartTime(
                Instant.parse("2026-07-14T00:05:00Z"));

        /*
         * LIVE 상세 구장 연결을 검증하기 위한 값이다.
         */
        game.setVenue("Wrigley Field");

        game.setPeriod(8);
        game.setHomeRuns(3);
        game.setAwayRuns(4);

        game.setHomeInningScores(
                List.of(
                        0,
                        0,
                        1,
                        0,
                        2,
                        0,
                        0,
                        0));

        game.setAwayInningScores(
                List.of(
                        0,
                        1,
                        0,
                        2,
                        0,
                        0,
                        1,
                        0));

        return game;
    }

    private static Game baseGame(long gameId) {
        Game game = new Game();

        game.setId(gameId);

        game.setHomeTeamId(1L);
        game.setHomeTeamName("Home Team");
        game.setHomeTeamAbbr("HOM");

        game.setAwayTeamId(2L);
        game.setAwayTeamName("Away Team");
        game.setAwayTeamAbbr("AWY");

        return game;
    }

    private static Lineup startingHitter(
            long gameId,
            long playerId,
            long teamId,
            int battingOrder,
            String position) {

        Lineup lineup = new Lineup();

        lineup.setGameId(gameId);
        lineup.setPlayerId(playerId);
        lineup.setTeamId(teamId);
        lineup.setBattingOrder(battingOrder);
        lineup.setPosition(position);
        lineup.setIsProbablePitcher(false);

        return lineup;
    }

    private static Lineup probablePitcher(
            long gameId,
            long playerId,
            long teamId) {

        Lineup lineup = new Lineup();

        lineup.setGameId(gameId);
        lineup.setPlayerId(playerId);
        lineup.setTeamId(teamId);
        lineup.setIsProbablePitcher(true);

        return lineup;
    }

    private static Player player(
            long playerId,
            String fullName) {

        Player player = new Player();

        player.setId(playerId);
        player.setFullName(fullName);

        return player;
    }

    private static Play scoringPlay() {
        Play play = new Play();

        play.setGameId(200L);
        play.setPlayOrder(1000L);
        play.setInning(2);
        play.setInningType("Bottom");
        play.setText("Díaz homered to left center.");
        play.setHomeScore(1);
        play.setAwayScore(0);
        play.setScoringPlay(true);

        return play;
    }

    private static Play livePlay() {
        Play play = new Play();

        play.setGameId(300L);
        play.setPlayOrder(2000L);

        play.setInning(8);
        play.setInningType("Top");

        play.setOuts(2);
        play.setBalls(3);
        play.setStrikes(2);

        play.setRunnerOnFirst(false);
        play.setRunnerOnSecond(true);
        play.setRunnerOnThird(false);

        return play;
    }
}