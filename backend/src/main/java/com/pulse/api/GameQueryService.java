package com.pulse.api;

import io.swagger.v3.oas.annotations.media.Schema;
import com.pulse.domain.*;
import com.pulse.gameprocessing.application.TensionCurveQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameQueryService {

    private final GameRepository gameRepository;

    /*
     * games의 팀 ID와 MLB 공식 로고용 ID는 서로 다를 수 있으므로
     * teams.logo_team_id를 조회하기 위해 사용한다.
     */
    private final TeamRepository teamRepository;

    private final PlayRepository playRepository;
    private final PlayerRepository playerRepository;
    private final LineupRepository lineupRepository;
    private final TensionCurveQueryService tensionCurveQueryService;
    private final SwitchSuggestionService switchSuggestionService;

    /**
     * 경기 상태와 사용자가 선택한 공개 모드에 맞는 상세 응답을 반환한다.
     *
     * 보호 모드에서는 점수, 초·말, 현재 타자·투수처럼
     * 경기 결과나 공격 팀을 드러낼 수 있는 필드를 응답에서 제거한다.
     */
    public GameDetailView getGameDetail(
            long gameId,
            String mode
    ) {
        return getGameDetail(
                gameId,
                mode,
                null
        );
    }

    public GameDetailView getGameDetail(
            long gameId,
            String mode,
            String username
    ) {

        Game game = findGame(gameId);
        DisplayMode safeMode = parseDisplayMode(mode);

        /*
         * 기존 클라이언트 호환을 위해 NORMAL은 공개 모드로 처리한다.
         * 신규 API와 프론트에서는 PROTECTED와 REVEALED만 사용한다.
         */
        boolean revealed =
                safeMode == DisplayMode.REVEALED
                        || safeMode == DisplayMode.NORMAL;

        /*
         * 종료 경기에는 현재 타석이나 B/S/O 상황이 존재하지 않는다.
         *
         * 마지막으로 수집된 play가 경기 종료 시점보다 오래된 데이터일 수도
         * 있으므로, 종료 경기는 play 기반 상황을 만들지 않고 전용 DTO로 분기한다.
         */
        if (game.isFinal()) {
            return finalGameDetail(game, revealed);
        }

        /*
         * 예정 경기에는 공개할 결과가 없다.
         *
         * mode 요청값과 관계없이 예정 경기 전용 응답을 반환하며,
         * 프론트에서는 보호/공개 토글을 표시하지 않는다.
         */
        if (Game.STATUS_SCHEDULED.equals(game.getStatus())) {
            return scheduledGameDetail(game);
        }

        Play latestPlay = latestPlay(gameId);

        TeamResponse homeTeam =
                team(
                        game.getHomeTeamId(),
                        game.getHomeTeamName(),
                        game.getHomeTeamAbbr());

        TeamResponse awayTeam =
                team(
                        game.getAwayTeamId(),
                        game.getAwayTeamName(),
                        game.getAwayTeamAbbr());

        Integer inning = currentInning(game, latestPlay);
        SituationResponse situation = situation(latestPlay);

        SwitchSuggestionResponse switchSuggestion =
                switchSuggestionService.findSuggestion(
                        gameId,
                        username
                );

        if (revealed) {
            return new RevealedGameDetailResponse(
                    game.getId(),
                    game.getStatus(),
                    DisplayMode.REVEALED,
                    homeTeam,
                    awayTeam,
                    game.getStartTime(),
                    nullableText(
                            game.getVenue()),
                    score(game),
                    inning,
                    latestPlay == null
                            ? null
                            : latestPlay.getInningType(),
                    situation,
                    currentMatchup(latestPlay),
                    favoritePlayersPlaying(),
                    inningScores(game),
                    switchSuggestion);
        }

        return new ProtectedGameDetailResponse(
                game.getId(),
                game.getStatus(),
                DisplayMode.PROTECTED,
                homeTeam,
                awayTeam,
                game.getStartTime(),
                periodLabel(game),
                inning,
                situation,
                favoritePlayersPlaying(),
                switchSuggestion);
    }

    private Game findGame(long gameId) {
        return gameRepository
                .findById(gameId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "game not found: " + gameId));
    }

    /**
     * 예정 경기 상세에서는 결과성 데이터 없이
     * 매치업, 시작 시각, 선발 투수와 양 팀 선발 라인업만 반환한다.
     *
     * 구장명은 홈·원정 관계를 유추할 수 있으므로 응답에서 제외한다.
     */
    private GameDetailView scheduledGameDetail(Game game) {
        TeamResponse homeTeam =
                team(
                        game.getHomeTeamId(),
                        game.getHomeTeamName(),
                        game.getHomeTeamAbbr());

        TeamResponse awayTeam =
                team(
                        game.getAwayTeamId(),
                        game.getAwayTeamName(),
                        game.getAwayTeamAbbr());

        return new ScheduledGameDetailResponse(
                game.getId(),
                game.getStatus(),
                DisplayMode.PROTECTED,
                homeTeam,
                awayTeam,
                game.getStartTime(),
                probablePitchers(game),
                startingLineups(game));
    }

    /**
     * lineups에서 예상 선발로 지정된 선수를 찾아
     * 홈·원정 팀별 선수 이름으로 변환한다.
     *
     * 선발이 아직 확정되지 않았거나 선수 정보가 없으면
     * 해당 home/away 값은 null로 반환한다.
     */
    private ProbablePitchersResponse probablePitchers(Game game) {
        List<Lineup> probablePitcherLineups =
                lineupRepository
                        .findByGameIdAndIsProbablePitcherTrue(
                                game.getId());

        if (probablePitcherLineups.isEmpty()) {
            return new ProbablePitchersResponse(
                    null,
                    null);
        }

        /*
         * 선수마다 개별 쿼리를 실행하지 않도록
         * 필요한 선수 ID를 한 번에 조회한다.
         */
        List<Long> playerIds =
                probablePitcherLineups
                        .stream()
                        .map(Lineup::getPlayerId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        Map<Long, Player> playersById =
                playerRepository
                        .findAllById(playerIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Player::getId,
                                        Function.identity()));

        String homePitcher = null;
        String awayPitcher = null;

        for (Lineup lineup : probablePitcherLineups) {
            Player player =
                    playersById.get(
                            lineup.getPlayerId());

            String name = playerName(player);

            if (name == null) {
                continue;
            }

            if (Objects.equals(
                    lineup.getTeamId(),
                    game.getHomeTeamId())) {

                if (homePitcher == null) {
                    homePitcher = name;
                }

                continue;
            }

            if (Objects.equals(
                    lineup.getTeamId(),
                    game.getAwayTeamId())
                    && awayPitcher == null) {

                awayPitcher = name;
            }
        }

        return new ProbablePitchersResponse(
                homePitcher,
                awayPitcher);
    }

    /**
     * batting_order가 있는 선수만 선발 라인업으로 사용한다.
     *
     * 선수 정보가 없거나 타순이 없는 행은 제외하고,
     * 홈·원정 팀별로 타순 오름차순 정렬해 반환한다.
     */
    private StartingLineupsResponse startingLineups(
            Game game) {

        List<Lineup> battingLineups =
                lineupRepository
                        .findByGameId(game.getId())
                        .stream()
                        .filter(
                                lineup ->
                                        lineup.getBattingOrder()
                                                != null)
                        .toList();

        if (battingLineups.isEmpty()) {
            return new StartingLineupsResponse(
                    List.of(),
                    List.of());
        }

        List<Long> playerIds =
                battingLineups
                        .stream()
                        .map(Lineup::getPlayerId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        Map<Long, Player> playersById =
                playerRepository
                        .findAllById(playerIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Player::getId,
                                        Function.identity()));

        List<StartingLineupPlayerResponse> homeLineup =
                new ArrayList<>();

        List<StartingLineupPlayerResponse> awayLineup =
                new ArrayList<>();

        for (Lineup lineup : battingLineups) {
            Player player =
                    playersById.get(
                            lineup.getPlayerId());

            String playerName =
                    playerName(player);

            Integer battingOrder =
                    lineup.getBattingOrder();

            if (playerName == null
                    || battingOrder == null) {
                continue;
            }

            StartingLineupPlayerResponse response =
                    new StartingLineupPlayerResponse(
                            battingOrder,
                            playerName,
                            nullableText(
                                    lineup.getPosition()));

            if (Objects.equals(
                    lineup.getTeamId(),
                    game.getHomeTeamId())) {

                homeLineup.add(response);
                continue;
            }

            if (Objects.equals(
                    lineup.getTeamId(),
                    game.getAwayTeamId())) {

                awayLineup.add(response);
            }
        }

        homeLineup.sort(
                (left, right) ->
                        Integer.compare(
                                left.battingOrder(),
                                right.battingOrder()));

        awayLineup.sort(
                (left, right) ->
                        Integer.compare(
                                left.battingOrder(),
                                right.battingOrder()));

        return new StartingLineupsResponse(
                homeLineup,
                awayLineup);
    }

    /**
     * 종료 경기는 진행 경기와 응답 필드가 다르므로
     * 마지막 play 기반 상황 정보 없이 전용 응답을 반환한다.
     */
    private GameDetailView finalGameDetail(
            Game game,
            boolean revealed) {

        TeamResponse homeTeam =
                team(
                        game.getHomeTeamId(),
                        game.getHomeTeamName(),
                        game.getHomeTeamAbbr());

        TeamResponse awayTeam =
                team(
                        game.getAwayTeamId(),
                        game.getAwayTeamName(),
                        game.getAwayTeamAbbr());

        if (revealed) {
            return new RevealedFinalGameDetailResponse(
                    game.getId(),
                    game.getStatus(),
                    DisplayMode.REVEALED,
                    homeTeam,
                    awayTeam,
                    game.getStartTime(),
                    nullableText(
                            game.getVenue()),
                    nullableText(
                            game.getFinalHeadlineRevealed()),
                    score(game),
                    inningScores(game),
                    scoringSummary(game.getId()),
                    revealedTensionCurve(game.getId()));
        }

        return new ProtectedFinalGameDetailResponse(
                game.getId(),
                game.getStatus(),
                DisplayMode.PROTECTED,
                homeTeam,
                awayTeam,
                game.getStartTime(),
                nullableText(
                        game.getFinalHeadlineProtected()),
                protectedTensionCurve(game.getId()));
    }

    /**
     * 보호 모드 경기 긴장도 그래프은 이닝 단위만 노출한다.
     *
     * 서버가 watch_scores의 base_score 이력을 1~5 단계로 양자화한
     * 결과만 DTO로 옮기며, 원 점수나 이벤트 마커는 포함하지 않는다.
     */
    private List<ProtectedTensionPointResponse> protectedTensionCurve(
            long gameId) {

        return tensionCurveQueryService
                .getProtectedCurve(gameId)
                .stream()
                .map(
                        point ->
                                new ProtectedTensionPointResponse(
                                        point.inning(),
                                        point.level()))
                .toList();
    }

    /**
     * 공개 모드 경기 긴장도 그래프은 하프이닝 단위까지 허용한다.
     *
     * 그래프는 점수 이력의 양자화 레벨만 사용하고,
     * 경기 흐름 목록과 연결되는 이벤트 정보는 포함하지 않는다.
     */
    private List<RevealedTensionPointResponse> revealedTensionCurve(
            long gameId) {

        return tensionCurveQueryService
                .getRevealedCurve(gameId)
                .stream()
                .map(
                        point ->
                                new RevealedTensionPointResponse(
                                        point.inning(),
                                        point.inningType(),
                                        point.level()))
                .toList();
    }

    /**
     * 현재 상황과 현재 타석은 가장 최근 play 스냅샷을 기준으로 만든다.
     */
    private Play latestPlay(long gameId) {
        return playRepository
                .findByGameIdOrderByPlayOrderDesc(
                        gameId,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * 득점 플레이는 plays 테이블에서 scoring_play=true인 행만 사용한다.
     *
     * 외부 API의 별도 scoring summary를 전달하거나,
     * 수집되지 않은 후반 득점 장면을 서버가 추측해서 생성하지 않는다.
     */
    private List<ScoringPlayResponse> scoringSummary(
            long gameId) {

        return playRepository
                .findByGameIdOrderByPlayOrderAsc(gameId)
                .stream()
                .filter(
                        play ->
                                Boolean.TRUE.equals(
                                        play.getScoringPlay()))
                .filter(
                        play ->
                                hasText(play.getText()))
                .map(
                        play ->
                                new ScoringPlayResponse(
                                        play.getInning(),
                                        play.getInningType(),
                                        play.getText().trim()))
                .toList();
    }

    private TeamResponse team(
            Long id,
            String name,
            String abbr) {

        /*
         * balldontlie 팀 ID와 MLB 로고용 ID는 서로 다를 수 있으므로
         * games의 팀 ID를 URL에 직접 사용하지 않고 teams에서 조회한다.
         */
        String logoUrl =
                id == null
                        ? null
                        : teamRepository
                                .findById(id)
                                .map(Team::getLogoTeamId)
                                .map(GameQueryService::teamLogoUrl)
                                .orElse(null);

        return new TeamResponse(
                id,
                name,
                abbr,
                logoUrl);
    }

    /**
     * teams.logo_team_id를 MLB 공식 팀 로고 주소로 변환한다.
     */
    private static String teamLogoUrl(
            Long logoTeamId) {

        if (logoTeamId == null) {
            return null;
        }

        return "https://www.mlbstatic.com/team-logos/"
                + logoTeamId
                + ".svg";
    }

    private static ScoreResponse score(Game game) {
        return new ScoreResponse(
                game.getHomeRuns(),
                game.getAwayRuns());
    }

    /**
     * 진행 경기에서는 play의 이닝을 우선 사용한다.
     *
     * 아직 play가 수집되지 않았다면 games.period를 사용한다.
     * 종료 경기는 이 메서드를 사용하지 않고 finalGameDetail로 분기된다.
     */
    private static Integer currentInning(
            Game game,
            Play latestPlay) {

        if (latestPlay != null
                && latestPlay.getInning() != null) {
            return latestPlay.getInning();
        }

        return game.getPeriod();
    }

    /**
     * 현재 타석이 없거나 이닝 교대 중이면 situation=null을 반환한다.
     */
    private static SituationResponse situation(Play play) {
        if (play == null) {
            return null;
        }

        boolean runnerOnFirst =
                Boolean.TRUE.equals(
                        play.getRunnerOnFirst());

        boolean runnerOnSecond =
                Boolean.TRUE.equals(
                        play.getRunnerOnSecond());

        boolean runnerOnThird =
                Boolean.TRUE.equals(
                        play.getRunnerOnThird());

        return new SituationResponse(
                play.getOuts(),
                play.getBalls(),
                play.getStrikes(),
                runnerOnFirst,
                runnerOnSecond,
                runnerOnThird,
                runnerOnSecond || runnerOnThird,
                runnerOnFirst
                        && runnerOnSecond
                        && runnerOnThird);
    }

    /**
     * 현재 타자와 투수는 공개 모드에서만 반환한다.
     *
     * 타자와 투수 중 하나라도 식별할 수 없으면
     * currentMatchup 전체를 null로 반환한다.
     */
    private CurrentMatchupResponse currentMatchup(
            Play play) {

        if (play == null
                || play.getBatterId() == null
                || play.getPitcherId() == null) {
            return null;
        }

        PlayerResponse batter =
                playerResponse(
                        play.getBatterId());

        PlayerResponse pitcher =
                playerResponse(
                        play.getPitcherId());

        if (batter == null || pitcher == null) {
            return null;
        }

        return new CurrentMatchupResponse(
                batter,
                pitcher);
    }

    private PlayerResponse playerResponse(Long playerId) {
        return playerRepository
                .findById(playerId)
                .map(
                        player -> {
                            String name =
                                    playerName(player);

                            if (name == null) {
                                return null;
                            }

                            return new PlayerResponse(
                                    player.getId(),
                                    name);
                        })
                .orElse(null);
    }

    /**
     * fullName을 우선 사용하고,
     * 없으면 firstName과 lastName을 조합한다.
     */
    private static String playerName(Player player) {
        if (player == null) {
            return null;
        }

        if (hasText(player.getFullName())) {
            return player.getFullName().trim();
        }

        String firstName =
                hasText(player.getFirstName())
                        ? player.getFirstName().trim()
                        : "";

        String lastName =
                hasText(player.getLastName())
                        ? player.getLastName().trim()
                        : "";

        String combinedName =
                (firstName + " " + lastName).trim();

        return combinedName.isBlank()
                ? null
                : combinedName;
    }

    private static boolean hasText(String value) {
        return value != null
                && !value.isBlank();
    }

    /**
     * DB의 빈 문자열은 의미 있는 화면 문구가 아니므로
     * API 응답에서는 null로 통일한다.
     */
    private static String nullableText(String value) {
        return hasText(value)
                ? value.trim()
                : null;
    }

    private static InningScoresResponse inningScores(
            Game game) {

        return new InningScoresResponse(
                safeInningScores(
                        game.getAwayInningScores()),
                safeInningScores(
                        game.getHomeInningScores()));
    }

    /**
     * 종료 경기의 마지막 홈 공격처럼 null 값이 포함될 수 있다.
     *
     * List.copyOf는 null 원소가 있으면 예외가 발생하므로
     * ArrayList로 복사한다.
     */
    private static List<Integer> safeInningScores(
            List<Integer> scores) {

        return scores == null
                ? List.of()
                : new ArrayList<>(scores);
    }

    /**
     * 관심 선수 정보는 인증 사용자 설정과 연결하는 후속 작업이다.
     *
     * 최신 응답 계약의 필드는 유지하되 현재 단계에서는
     * 빈 목록을 반환한다.
     */
    private static List<String> favoritePlayersPlaying() {
        return List.of();
    }

    /**
     * mode가 없거나 잘못된 값이면 보호 모드로 처리한다.
     *
     * 잘못된 요청 때문에 공개 응답이 반환되는 것을 방지한다.
     */
    private static DisplayMode parseDisplayMode(
            String mode) {

        if (mode == null || mode.isBlank()) {
            return DisplayMode.PROTECTED;
        }

        String normalizedMode =
                mode.trim().toUpperCase();

        try {
            return DisplayMode.valueOf(
                    normalizedMode);
        } catch (IllegalArgumentException exception) {
            return DisplayMode.PROTECTED;
        }
    }

    private static String periodLabel(Game game) {
        if (game.isFinal()) {
            return "경기 종료";
        }

        if (!game.isLive()) {
            return "경기 전";
        }

        Integer period = game.getPeriod();

        if (period == null) {
            return "진행 중";
        }

        if (period >= 10) {
            return "연장";
        }

        if (period >= 7) {
            return "후반";
        }

        if (period >= 4) {
            return "중반";
        }

        return "초반";
    }

    public enum DisplayMode {
        PROTECTED,
        REVEALED,

        /*
         * 기존 호출 호환용이다.
         * 신규 API와 프론트에서는 사용하지 않는다.
         */
        NORMAL
    }

    /**
     * 경기 상태와 공개 모드에 따라 서로 다른 DTO를 반환하기 위한
     * 공통 응답 타입이다.
     */
    @Schema(
            description = "경기 상태와 표시 모드에 따라 달라지는 경기 상세 응답",
            oneOf = {
                    ScheduledGameDetailResponse.class,
                    ProtectedGameDetailResponse.class,
                    RevealedGameDetailResponse.class,
                    ProtectedFinalGameDetailResponse.class,
                    RevealedFinalGameDetailResponse.class
            }
    )
    public sealed interface GameDetailView
            permits ScheduledGameDetailResponse,
            ProtectedGameDetailResponse,
            RevealedGameDetailResponse,
            ProtectedFinalGameDetailResponse,
            RevealedFinalGameDetailResponse {}

    /**
     * 예정 경기 상세 응답이다.
     *
     * 공개할 결과가 없으므로 항상 PROTECTED이며,
     * 점수·이닝·현재 상황·이벤트·AI 문구를 포함하지 않는다.
     */
    public record ScheduledGameDetailResponse(
            long gameId,
            String status,
            DisplayMode displayMode,
            TeamResponse homeTeam,
            TeamResponse awayTeam,
            Instant startTime,
            ProbablePitchersResponse probablePitchers,
            StartingLineupsResponse startingLineups)
            implements GameDetailView {}

    /**
     * 진행 경기 보호 응답이다.
     *
     * 팀 정보, 이닝 숫자, 현재 상황은 제공하지만
     * 점수, 초·말, 현재 타자·투수는 제공하지 않는다.
     */
    public record ProtectedGameDetailResponse(
            long gameId,
            String status,
            DisplayMode displayMode,
            TeamResponse homeTeam,
            TeamResponse awayTeam,
            Instant startTime,
            String periodLabel,
            Integer inning,
            SituationResponse situation,
            List<String> favoritePlayersPlaying,
            SwitchSuggestionResponse switchSuggestion)
            implements GameDetailView {}

    /**
     * 진행 경기 공개 응답이다.
     */
    public record RevealedGameDetailResponse(
            long gameId,
            String status,
            DisplayMode displayMode,
            TeamResponse homeTeam,
            TeamResponse awayTeam,
            Instant startTime,
            String venue,
            ScoreResponse score,
            Integer inning,
            String inningType,
            SituationResponse situation,
            CurrentMatchupResponse currentMatchup,
            List<String> favoritePlayersPlaying,
            InningScoresResponse inningScores,
            SwitchSuggestionResponse switchSuggestion)
            implements GameDetailView {}

    /**
     * 종료 경기 보호 응답이다.
     *
     * 점수, 초·말, 득점 플레이처럼 결과를 드러내는 필드를
     * 포함하지 않는다.
     */
    public record ProtectedFinalGameDetailResponse(
            long gameId,
            String status,
            DisplayMode displayMode,
            TeamResponse homeTeam,
            TeamResponse awayTeam,
            Instant startTime,
            String headline,
            List<ProtectedTensionPointResponse> tensionCurve)
            implements GameDetailView {}

    /**
     * 종료 경기 공개 응답이다.
     *
     * 진행 경기의 현재 상황 대신 최종 점수와
     * 득점 플레이 목록을 제공한다.
     */
    public record RevealedFinalGameDetailResponse(
            long gameId,
            String status,
            DisplayMode displayMode,
            TeamResponse homeTeam,
            TeamResponse awayTeam,
            Instant startTime,
            String venue,
            String headline,
            ScoreResponse finalScore,
            InningScoresResponse inningScores,
            List<ScoringPlayResponse> scoringSummary,
            List<RevealedTensionPointResponse> tensionCurve)
            implements GameDetailView {}

    public record TeamResponse(
            Long id,
            String name,
            String abbr,
            String logoUrl) {}

    public record ScoreResponse(
            Integer home,
            Integer away) {}

    public record SituationResponse(
            Integer outs,
            Integer balls,
            Integer strikes,
            boolean runnerOnFirst,
            boolean runnerOnSecond,
            boolean runnerOnThird,
            boolean scoringPosition,
            boolean basesLoaded) {}

    public record PlayerResponse(
            Long id,
            String name) {}

    public record CurrentMatchupResponse(
            PlayerResponse batter,
            PlayerResponse pitcher) {}

    public record InningScoresResponse(
            List<Integer> away,
            List<Integer> home) {}

    public record ProbablePitchersResponse(
            String home,
            String away) {}

    public record StartingLineupsResponse(
            List<StartingLineupPlayerResponse> home,
            List<StartingLineupPlayerResponse> away) {}

    public record StartingLineupPlayerResponse(
            Integer battingOrder,
            String playerName,
            String position) {}

    public record ScoringPlayResponse(
            Integer inning,
            String inningType,
            String text) {}

    /**
     * 보호 모드의 경기 흐름은 이닝 숫자와
     * 1~5 단계 값만 제공한다.
     */
    public record ProtectedTensionPointResponse(
            Integer inning,
            Integer level) {}

    /**
     * 공개 모드의 경기 흐름은 필요할 경우
     * 하프이닝 단위까지 제공할 수 있다.
     */
    public record RevealedTensionPointResponse(
            Integer inning,
            String inningType,
            Integer level) {}

    public record SwitchSuggestionResponse(
            long gameId,
            MatchupResponse matchup,
            String latestTag,
            String message) {}

    public record MatchupResponse(
            String home,
            String away) {}
}
