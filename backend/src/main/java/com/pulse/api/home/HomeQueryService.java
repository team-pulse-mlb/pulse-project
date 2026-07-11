package com.pulse.api.home;

import com.pulse.domain.Game;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Lineup;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.ranking.RankingService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HomeQueryService {

    private static final ZoneId SLATE_ZONE = ZoneId.of("America/New_York");
    private static final int HOME_RANKING_LIMIT = 5;
    private static final int MAX_RANKING_LOOKUP = 1000;
    private static final int SCHEDULED_LOOKAHEAD_HOURS = 36;
    private static final int FINISHED_LOOKBACK_HOURS = 48;

    private final GameRepository gameRepository;
    private final WatchScoreRepository watchScoreRepository;
    private final GameEventRepository gameEventRepository;
    private final LineupRepository lineupRepository;
    private final PlayerRepository playerRepository;
    private final RankingService rankingService;
    private final StringRedisTemplate redisTemplate;

    public HomeRankingResponse getRanking(int count) {
        Instant now = Instant.now();
        int safeCount = rankingLimit(count);
        List<RankingLiveGameCard> live = rankingService.topLive(safeCount).keySet().stream()
                .map(gameRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(this::toRankingLiveCard)
                .toList();

        int remaining = safeCount - live.size();
        if (remaining <= 0) {
            return new HomeRankingResponse(now, live, List.of(), List.of());
        }

        List<Game> scheduledCandidates = gameRepository.findByStatusAndStartTimeBetween(
                        Game.STATUS_SCHEDULED,
                        now,
                        now.plusSeconds(SCHEDULED_LOOKAHEAD_HOURS * 60L * 60L))
                .stream()
                .filter(game -> isScheduledForHome(game, now))
                .sorted(scheduledRankingComparator())
                .toList();
        List<Game> finishedCandidates = gameRepository
                .findByStatusStartingWithAndStartTimeGreaterThanEqual(
                        Game.STATUS_FINAL,
                        now.minusSeconds(FINISHED_LOOKBACK_HOURS * 60L * 60L))
                .stream()
                .filter(game -> isFinishedForHome(game, now))
                .sorted(finishedRankingComparator())
                .toList();

        int scheduledReserve = scheduledCandidates.isEmpty() ? 0 : 1;
        int finishedLimit = Math.max(0, remaining - scheduledReserve);
        List<RankingFinishedGameCard> finished = finishedCandidates.stream()
                .limit(finishedLimit)
                .map(this::toRankingFinishedCard)
                .toList();

        remaining -= finished.size();
        List<Game> selectedScheduled = scheduledCandidates.stream()
                .limit(remaining)
                .toList();
        Map<Long, ProbablePitchersResponse> probablePitchers = probablePitchersByGame(selectedScheduled);
        List<RankingScheduledGameCard> scheduled = selectedScheduled.stream()
                .map(game -> toRankingScheduledCard(game, probablePitchers.get(game.getId())))
                .toList();

        return new HomeRankingResponse(now, live, scheduled, finished);
    }

    public HomeSlateResponse getSlate(String date, String status, String sort) {
        LocalDate slateDate = parseSlateDate(date);
        Instant startInclusive = slateDate.atStartOfDay(SLATE_ZONE).toInstant();
        Instant endExclusive = slateDate.plusDays(1).atStartOfDay(SLATE_ZONE).toInstant();
        String normalizedStatus = normalizeStatus(status);
        String normalizedSort = normalizeSort(sort, normalizedStatus);
        Map<Long, Double> liveScores = "recommended".equals(normalizedSort)
                ? rankingService.topLive(MAX_RANKING_LOOKUP)
                : Map.of();

        List<Game> selectedGames = gameRepository
                .findByStartTimeGreaterThanEqualAndStartTimeLessThan(startInclusive, endExclusive)
                .stream()
                .filter(game -> inSlate(game, startInclusive, endExclusive))
                .filter(game -> matchesStatus(game, normalizedStatus))
                .sorted(gameComparator(normalizedSort, liveScores))
                .toList();
        Map<Long, ProbablePitchersResponse> probablePitchers = probablePitchersByGame(selectedGames);
        List<SlateGameCard> games = selectedGames.stream()
                .map(game -> toSlateCard(game, probablePitchers.get(game.getId())))
                .toList();

        return new HomeSlateResponse(slateDate, games);
    }

    private RankingLiveGameCard toRankingLiveCard(Game game) {
        return new RankingLiveGameCard(
                game.getId(),
                matchup(game),
                game.getPeriod(),
                latestTag(game)
        );
    }

    private RankingScheduledGameCard toRankingScheduledCard(
            Game game,
            ProbablePitchersResponse probablePitchers
    ) {
        return new RankingScheduledGameCard(
                game.getId(),
                matchup(game),
                game.getStartTime(),
                game.getVenue(),
                probablePitchers
        );
    }

    private RankingFinishedGameCard toRankingFinishedCard(Game game) {
        return new RankingFinishedGameCard(
                game.getId(),
                matchup(game),
                headline(game),
                keyMoment(game)
        );
    }

    private SlateGameCard toSlateCard(Game game, ProbablePitchersResponse probablePitchers) {
        String state = stateOf(game);
        if (game.isLive()) {
            return new SlateLiveGameCard(
                    game.getId(),
                    state,
                    matchup(game),
                    game.getStartTime(),
                    game.getPeriod(),
                    latestTag(game)
            );
        }
        if (game.isFinal()) {
            return new SlateFinishedGameCard(
                    game.getId(),
                    state,
                    matchup(game),
                    game.getStartTime(),
                    headline(game),
                    keyMoment(game)
            );
        }
        return new SlateScheduledGameCard(
                game.getId(),
                state,
                matchup(game),
                game.getStartTime(),
                game.getVenue(),
                probablePitchers
        );
    }

    private Map<Long, ProbablePitchersResponse> probablePitchersByGame(List<Game> games) {
        Map<Long, Game> scheduledGames = games.stream()
                .filter(game -> Game.STATUS_SCHEDULED.equals(game.getStatus()))
                .collect(Collectors.toMap(Game::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        if (scheduledGames.isEmpty()) {
            return Map.of();
        }

        List<Lineup> probablePitchers = lineupRepository.findByGameIdInAndIsProbablePitcherTrue(
                List.copyOf(scheduledGames.keySet()));
        Map<Long, Player> playersById = playerRepository.findAllById(
                        probablePitchers.stream().map(Lineup::getPlayerId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));
        Map<Long, String> homePitchers = new HashMap<>();
        Map<Long, String> awayPitchers = new HashMap<>();

        for (Lineup lineup : probablePitchers) {
            Game game = scheduledGames.get(lineup.getGameId());
            Player player = playersById.get(lineup.getPlayerId());
            String name = playerName(player);
            if (game == null || name == null) {
                continue;
            }
            if (java.util.Objects.equals(lineup.getTeamId(), game.getHomeTeamId())) {
                homePitchers.putIfAbsent(game.getId(), name);
            } else if (java.util.Objects.equals(lineup.getTeamId(), game.getAwayTeamId())) {
                awayPitchers.putIfAbsent(game.getId(), name);
            }
        }

        Map<Long, ProbablePitchersResponse> result = new LinkedHashMap<>();
        scheduledGames.keySet().forEach(gameId -> result.put(
                gameId,
                new ProbablePitchersResponse(homePitchers.get(gameId), awayPitchers.get(gameId))
        ));
        return result;
    }

    private static String playerName(Player player) {
        if (player == null) {
            return null;
        }
        if (player.getFullName() != null && !player.getFullName().isBlank()) {
            return player.getFullName();
        }
        String fallback = java.util.stream.Stream.of(player.getFirstName(), player.getLastName())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
        return fallback.isBlank() ? null : fallback;
    }

    private Comparator<Game> gameComparator(String sort, Map<Long, Double> liveScores) {
        if ("recommended".equals(sort)) {
            return Comparator
                    .comparing((Game game) -> recommendedScore(game, liveScores)).reversed()
                    .thenComparing(HomeQueryService::startTimeOrMax);
        }
        return Comparator
                .comparing((Game game) -> !game.isLive())
                .thenComparing(HomeQueryService::startTimeOrMax);
    }

    private static double recommendedScore(Game game, Map<Long, Double> liveScores) {
        if (game.isLive()) {
            return liveScores.getOrDefault(game.getId(), -1.0);
        }
        if (game.isFinal()) {
            return scoreOrMin(game.getPeakBaseScore());
        }
        if (Game.STATUS_SCHEDULED.equals(game.getStatus())) {
            return scoreOrMin(game.getPregameScore());
        }
        return Integer.MIN_VALUE;
    }

    private static Instant startTimeOrMax(Game game) {
        return game.getStartTime() == null ? Instant.MAX : game.getStartTime();
    }

    private static int rankingLimit(int count) {
        if (count <= 0) {
            return HOME_RANKING_LIMIT;
        }
        return Math.min(count, HOME_RANKING_LIMIT);
    }

    private static boolean isScheduledForHome(Game game, Instant now) {
        Instant startTime = game.getStartTime();
        return Game.STATUS_SCHEDULED.equals(game.getStatus())
                && startTime != null
                && !startTime.isBefore(now)
                && !startTime.isAfter(now.plusSeconds(SCHEDULED_LOOKAHEAD_HOURS * 60L * 60L));
    }

    private static boolean isFinishedForHome(Game game, Instant now) {
        Instant startTime = game.getStartTime();
        return game.isFinal()
                && startTime != null
                && !startTime.isBefore(now.minusSeconds(FINISHED_LOOKBACK_HOURS * 60L * 60L));
    }

    private static Comparator<Game> scheduledRankingComparator() {
        return Comparator
                .comparingInt((Game game) -> scoreOrMin(game.getPregameScore())).reversed()
                .thenComparing(HomeQueryService::startTimeOrMax);
    }

    private static Comparator<Game> finishedRankingComparator() {
        return Comparator
                .comparingInt((Game game) -> scoreOrMin(game.getPeakBaseScore())).reversed()
                .thenComparing(HomeQueryService::startTimeOrMax);
    }

    private static int scoreOrMin(Integer score) {
        return score == null ? Integer.MIN_VALUE : score;
    }

    private static boolean inSlate(Game game, Instant startInclusive, Instant endExclusive) {
        Instant startTime = game.getStartTime();
        return startTime != null && !startTime.isBefore(startInclusive) && startTime.isBefore(endExclusive);
    }

    private static boolean matchesStatus(Game game, String status) {
        return switch (status) {
            case "scheduled" -> Game.STATUS_SCHEDULED.equals(game.getStatus());
            case "live" -> game.isLive();
            case "finished" -> game.isFinal();
            default -> true;
        };
    }

    private static LocalDate parseSlateDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(SLATE_ZONE);
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException exception) {
            throw new InvalidSlateDateException("날짜는 YYYY-MM-DD 형식이어야 합니다.", exception);
        }
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "all";
        }
        String normalized = status.trim().toLowerCase();
        return switch (normalized) {
            case "scheduled", "live", "finished" -> normalized;
            default -> "all";
        };
    }

    private static String normalizeSort(String sort, String status) {
        if (sort == null || sort.isBlank()) {
            return "startTime";
        }
        String normalized = sort.trim().toLowerCase();
        if ("recommended".equals(normalized) && !"all".equals(status)) {
            return normalized;
        }
        return "startTime";
    }

    private static String stateOf(Game game) {
        if (game.isLive()) {
            return "LIVE";
        }
        if (game.isFinal()) {
            return "FINAL";
        }
        if (Game.STATUS_SCHEDULED.equals(game.getStatus())) {
            return "SCHEDULED";
        }
        if (Game.STATUS_POSTPONED.equals(game.getStatus())) {
            return "POSTPONED";
        }
        if (Game.STATUS_CANCELED.equals(game.getStatus())) {
            return "CANCELED";
        }
        return "UNKNOWN";
    }

    private MatchupResponse matchup(Game game) {
        return new MatchupResponse(
                teamLabel(game.getHomeTeamName(), game.getHomeTeamAbbr()),
                teamLabel(game.getAwayTeamName(), game.getAwayTeamAbbr())
        );
    }

    private static String teamLabel(String name, String abbr) {
        if (abbr != null && !abbr.isBlank()) {
            return abbr;
        }
        return name;
    }

    private String latestTag(Game game) {
        if (!game.isLive()) {
            return null;
        }
        String cached = valueOf(redisTemplate.opsForHash().get(liveCacheKey(game.getId()), "latestTag"));
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        return watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(game.getId())
                .map(WatchScore::getTags)
                .filter(tags -> tags != null && !tags.isEmpty())
                .map(tags -> tags.get(tags.size() - 1))
                .orElse(null);
    }

    private static String headline(Game game) {
        if (game.isFinal()
                && game.getFinalHeadlineProtected() != null
                && !game.getFinalHeadlineProtected().isBlank()) {
            return game.getFinalHeadlineProtected();
        }
        return null;
    }

    private String keyMoment(Game game) {
        if (!game.isFinal()) {
            return null;
        }
        return gameEventRepository
                .findFirstByGameIdAndSpoilerLevelOrderByObservedAtDescIdDesc(
                        game.getId(), GameEvent.SPOILER_PROTECTED_SAFE)
                .map(GameEvent::getEventType)
                .map(HomeQueryService::eventLabel)
                .orElse(null);
    }

    private static String eventLabel(String eventType) {
        return switch (eventType) {
            case "pressure_bases_loaded" -> "만루 승부";
            case "pressure_scoring_position" -> "득점권 승부";
            case "long_at_bat" -> "긴 접전 승부";
            case "full_count_two_out" -> "승부처 카운트";
            case "pitcher_instability" -> "투수 흔들림";
            case "hard_contact" -> "강한 타구";
            default -> null;
        };
    }

    private static String valueOf(Object value) {
        return value == null ? null : value.toString();
    }

    private static String liveCacheKey(long gameId) {
        return "game:" + gameId + ":live";
    }

    public record HomeRankingResponse(
            Instant generatedAt,
            List<RankingLiveGameCard> live,
            List<RankingScheduledGameCard> scheduled,
            List<RankingFinishedGameCard> finished
    ) {
    }

    public record HomeSlateResponse(
            LocalDate slateDate,
            List<SlateGameCard> games
    ) {
    }

    public interface SlateGameCard {
        long gameId();

        String gameState();

        Instant startTime();
    }

    public record RankingLiveGameCard(
            long gameId,
            MatchupResponse matchup,
            Integer inning,
            String latestTag
    ) {
    }

    public record RankingScheduledGameCard(
            long gameId,
            MatchupResponse matchup,
            Instant startTime,
            String venue,
            ProbablePitchersResponse probablePitchers
    ) {
    }

    public record RankingFinishedGameCard(
            long gameId,
            MatchupResponse matchup,
            String headline,
            String keyMoment
    ) {
    }

    public record SlateLiveGameCard(
            long gameId,
            String gameState,
            MatchupResponse matchup,
            Instant startTime,
            Integer inning,
            String latestTag
    ) implements SlateGameCard {
    }

    public record SlateScheduledGameCard(
            long gameId,
            String gameState,
            MatchupResponse matchup,
            Instant startTime,
            String venue,
            ProbablePitchersResponse probablePitchers
    ) implements SlateGameCard {
    }

    public record SlateFinishedGameCard(
            long gameId,
            String gameState,
            MatchupResponse matchup,
            Instant startTime,
            String headline,
            String keyMoment
    ) implements SlateGameCard {
    }

    public record MatchupResponse(String home, String away) {
    }

    public record ProbablePitchersResponse(String home, String away) {
    }
}
