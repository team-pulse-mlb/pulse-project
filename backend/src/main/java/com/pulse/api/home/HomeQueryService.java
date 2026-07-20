package com.pulse.api.home;

import io.swagger.v3.oas.annotations.media.Schema;
import com.pulse.domain.Game;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.GameEventLabelPolicy;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Lineup;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.ranking.RankingService;
import com.pulse.ranking.PersonalizationCalculator;
import com.pulse.common.time.SlateZone;
import com.pulse.common.user.UserPreferenceReader;
import com.pulse.common.user.UserPreferenceReader.UserPreferences;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HomeQueryService {

    private static final ZoneId SLATE_ZONE = SlateZone.ID;
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
    private final PersonalizationCalculator personalizationCalculator;
    private final Optional<UserPreferenceReader> userPreferenceReader;
    private final StringRedisTemplate redisTemplate;
    private final AnonymousHomeRankingCache anonymousRankingCache;

    public HomeRankingResponse getRanking(int count) {
        return getRanking(count, null);
    }

    public HomeRankingResponse getRanking(int count, String username) {
        int safeCount = rankingLimit(count);
        if (username == null || username.isBlank()) {
            return anonymousRankingCache.get(safeCount, () -> loadRanking(safeCount, null));
        }
        return loadRanking(safeCount, username);
    }

    private HomeRankingResponse loadRanking(int safeCount, String username) {
        Instant now = Instant.now();
        UserPreferences preferences = preferencesFor(username);
        Map<Long, Double> liveScores = rankingService.topLive(MAX_RANKING_LOOKUP);
        List<Long> rankedGameIds = List.copyOf(liveScores.keySet());
        Map<Long, Game> rankedGames = gameRepository.findAllById(rankedGameIds).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        rankedGameIds.stream()
                .filter(gameId -> !Optional.ofNullable(rankedGames.get(gameId))
                        .filter(Game::isLive)
                        .isPresent())
                .forEach(rankingService::removeLive);
        List<Game> liveCandidates = rankedGameIds.stream()
                .map(rankedGames::get)
                .filter(java.util.Objects::nonNull)
                .filter(Game::isLive)
                .toList();
        Map<Long, Set<Long>> liveLineups = lineupPlayerIdsByGame(liveCandidates, preferences);
        List<Game> selectedLive = liveCandidates.stream()
                .sorted(personalizedComparator(
                        game -> liveScores.getOrDefault(game.getId(), -1.0),
                        liveLineups,
                        preferences))
                .limit(safeCount)
                .toList();
        Map<Long, String> latestTags = latestTagsByGame(selectedLive);
        List<RankingLiveGameCard> live = selectedLive.stream()
                .map(game -> toRankingLiveCard(game, latestTags.get(game.getId())))
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
                .toList();
        List<Game> finishedCandidates = gameRepository
                .findByStatusStartingWithAndStartTimeGreaterThanEqual(
                        Game.STATUS_FINAL,
                        now.minusSeconds(FINISHED_LOOKBACK_HOURS * 60L * 60L))
                .stream()
                .filter(game -> isFinishedForHome(game, now))
                .toList();

        // 최근 창(종료 48시간·예정 36시간) 안 경기만으로 추천 5개를 못 채우면
        // 창 밖 최근 종료 경기와 다음 예정 경기로 보충해 추천 영역이 사라지지 않게 한다.
        if (live.size() + scheduledCandidates.size() + finishedCandidates.size() < safeCount) {
            finishedCandidates = fillFinishedFallback(finishedCandidates, safeCount);
            scheduledCandidates = fillScheduledFallback(scheduledCandidates, now, safeCount);
        }

        List<Game> nonLiveCandidates = Stream
                .concat(scheduledCandidates.stream(), finishedCandidates.stream())
                .toList();
        Map<Long, Set<Long>> nonLiveLineups = lineupPlayerIdsByGame(nonLiveCandidates, preferences);
        scheduledCandidates = scheduledCandidates.stream()
                .sorted(personalizedComparator(
                        game -> scoreOrMin(game.getPregameScore()), nonLiveLineups, preferences))
                .toList();
        finishedCandidates = finishedCandidates.stream()
                .sorted(personalizedComparator(
                        game -> scoreOrMin(game.getPeakBaseScore()), nonLiveLineups, preferences))
                .toList();

        int scheduledReserve = scheduledCandidates.isEmpty() ? 0 : 1;
        int finishedLimit = Math.max(0, remaining - scheduledReserve);
        List<Game> selectedFinished = finishedCandidates.stream()
                .limit(finishedLimit)
                .toList();
        Map<Long, String> keyMoments = keyMomentsByGame(selectedFinished);
        List<RankingFinishedGameCard> finished = selectedFinished.stream()
                .map(game -> toRankingFinishedCard(game, keyMoments.get(game.getId())))
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
        return getSlate(date, status, sort, null);
    }

    public HomeSlateResponse getSlate(String date, String status, String sort, String username) {
        LocalDate slateDate = parseSlateDate(date);
        Instant startInclusive = slateDate.atStartOfDay(SLATE_ZONE).toInstant();
        Instant endExclusive = slateDate.plusDays(1).atStartOfDay(SLATE_ZONE).toInstant();
        String normalizedStatus = normalizeStatus(status);
        String normalizedSort = normalizeSort(sort, normalizedStatus);
        Instant now = Instant.now();
        Map<Long, Double> liveScores = "recommended".equals(normalizedSort)
                ? rankingService.topLive(MAX_RANKING_LOOKUP)
                : Map.of();

        List<Game> candidates = "scheduled".equals(normalizedStatus)
                ? gameRepository.findByStatusAndStartTimeGreaterThanEqual(Game.STATUS_SCHEDULED, now)
                        .stream()
                        .filter(game -> isUpcomingScheduled(game, now))
                        .toList()
                : gameRepository.findByStartTimeGreaterThanEqualAndStartTimeLessThan(startInclusive, endExclusive)
                        .stream()
                        .filter(game -> inSlate(game, startInclusive, endExclusive))
                        .filter(game -> matchesStatus(game, normalizedStatus))
                        .toList();
        UserPreferences preferences = preferencesFor(username);
        Map<Long, Set<Long>> lineupPlayerIds = lineupPlayerIdsByGame(candidates, preferences);
        List<Game> selectedGames = candidates.stream()
                .sorted(gameComparator(normalizedSort, liveScores, lineupPlayerIds, preferences))
                .toList();
        Map<Long, ProbablePitchersResponse> probablePitchers = probablePitchersByGame(selectedGames);
        List<SlateGameCard> games = selectedGames.stream()
                .map(game -> toSlateCard(game, probablePitchers.get(game.getId())))
                .toList();

        return new HomeSlateResponse(slateDate, games);
    }

    private RankingLiveGameCard toRankingLiveCard(Game game, String latestTag) {
        return new RankingLiveGameCard(
                game.getId(),
                matchup(game),
                game.getPeriod(),
                latestTag
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

    private RankingFinishedGameCard toRankingFinishedCard(Game game, String keyMoment) {
        return new RankingFinishedGameCard(
                game.getId(),
                matchup(game),
                headline(game),
                keyMoment
        );
    }

    private Map<Long, String> latestTagsByGame(List<Game> games) {
        Map<Long, String> result = new HashMap<>();
        List<Long> missingGameIds = games.stream()
                .map(Game::getId)
                .filter(gameId -> {
                    String cached = valueOf(redisTemplate.opsForHash().get(liveCacheKey(gameId), "latestTag"));
                    if (cached == null || cached.isBlank()) {
                        return true;
                    }
                    result.put(gameId, cached);
                    return false;
                })
                .toList();
        if (!missingGameIds.isEmpty()) {
            watchScoreRepository.findLatestByGameIdIn(missingGameIds).forEach(score -> {
                List<String> tags = score.getTags();
                if (tags != null && !tags.isEmpty()) {
                    result.put(score.getGameId(), tags.get(tags.size() - 1));
                }
            });
        }
        return result;
    }

    private Map<Long, String> keyMomentsByGame(List<Game> games) {
        if (games.isEmpty()) {
            return Map.of();
        }
        return gameEventRepository.findLatestByGameIdInAndSpoilerLevel(
                        games.stream().map(Game::getId).toList(),
                        GameEvent.SPOILER_PROTECTED_SAFE)
                .stream()
                .collect(Collectors.toMap(
                        GameEvent::getGameId,
                        event -> GameEventLabelPolicy.protectedLabel(
                                event.getSpoilerLevel(), event.getEventType()),
                        (left, right) -> left));
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

    private Comparator<Game> gameComparator(
            String sort,
            Map<Long, Double> liveScores,
            Map<Long, Set<Long>> lineupPlayerIds,
            UserPreferences preferences
    ) {
        if ("recommended".equals(sort)) {
            return personalizedComparator(
                    game -> recommendedScore(game, liveScores), lineupPlayerIds, preferences);
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
        return isUpcomingScheduled(game, now)
                && !game.getStartTime().isAfter(now.plusSeconds(SCHEDULED_LOOKAHEAD_HOURS * 60L * 60L));
    }

    private static boolean isUpcomingScheduled(Game game, Instant now) {
        Instant startTime = game.getStartTime();
        return Game.STATUS_SCHEDULED.equals(game.getStatus())
                && startTime != null
                && !startTime.isBefore(now);
    }

    private static boolean isFinishedForHome(Game game, Instant now) {
        Instant startTime = game.getStartTime();
        return game.isFinal()
                && startTime != null
                && !startTime.isBefore(now.minusSeconds(FINISHED_LOOKBACK_HOURS * 60L * 60L));
    }

    private static int scoreOrMin(Integer score) {
        return score == null ? Integer.MIN_VALUE : score;
    }

    /** 창 안 종료 경기에 창 밖 최근 종료 경기(시작 시각 내림차순)를 중복 없이 이어붙인다. */
    private List<Game> fillFinishedFallback(List<Game> windowed, int limit) {
        List<Game> recent = gameRepository.findByStatusStartingWith(
                        Game.STATUS_FINAL,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "startTime")))
                .stream()
                .filter(Game::isFinal)
                .filter(game -> game.getStartTime() != null)
                .toList();
        return mergeDistinctById(windowed, recent);
    }

    /** 창 안 예정 경기에 창 밖 다음 예정 경기(시작 시각 오름차순)를 중복 없이 이어붙인다. */
    private List<Game> fillScheduledFallback(List<Game> windowed, Instant now, int limit) {
        List<Game> upcoming = gameRepository.findByStatusAndStartTimeGreaterThanEqual(
                        Game.STATUS_SCHEDULED,
                        now,
                        PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, "startTime")))
                .stream()
                .filter(game -> Game.STATUS_SCHEDULED.equals(game.getStatus()))
                .filter(game -> game.getStartTime() != null)
                .toList();
        return mergeDistinctById(windowed, upcoming);
    }

    private static List<Game> mergeDistinctById(List<Game> primary, List<Game> extra) {
        Map<Long, Game> byId = new LinkedHashMap<>();
        primary.forEach(game -> byId.putIfAbsent(game.getId(), game));
        extra.forEach(game -> byId.putIfAbsent(game.getId(), game));
        return List.copyOf(byId.values());
    }

    private Comparator<Game> personalizedComparator(
            java.util.function.ToDoubleFunction<Game> baseScore,
            Map<Long, Set<Long>> lineupPlayerIds,
            UserPreferences preferences
    ) {
        return Comparator
                .comparingDouble((Game game) -> baseScore.applyAsDouble(game)
                        + personalizationCalculator.bonus(
                                game,
                                lineupPlayerIds.getOrDefault(game.getId(), Set.of()),
                                preferences))
                .reversed()
                .thenComparing(HomeQueryService::startTimeOrMax);
    }

    private Map<Long, Set<Long>> lineupPlayerIdsByGame(
            List<Game> games,
            UserPreferences preferences
    ) {
        if (games.isEmpty() || preferences.favoritePlayerIds().isEmpty()) {
            return Map.of();
        }
        return lineupRepository.findByGameIdIn(games.stream().map(Game::getId).toList()).stream()
                .collect(Collectors.groupingBy(
                        Lineup::getGameId,
                        Collectors.mapping(Lineup::getPlayerId, Collectors.toSet())));
    }

    private UserPreferences preferencesFor(String username) {
        if (username == null || username.isBlank()) {
            return UserPreferences.empty();
        }
        return userPreferenceReader
                .map(reader -> reader.findByEmail(username))
                .orElseGet(UserPreferences::empty);
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
            return "scheduled".equals(status) ? "startTime" : "recommended";
        }
        String normalized = sort.trim().toLowerCase();
        if ("recommended".equals(normalized)) {
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
                .map(event -> GameEventLabelPolicy.protectedLabel(event.getSpoilerLevel(), event.getEventType()))
                .orElse(null);
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

    @Schema(
            description = "경기 상태에 따라 필드가 달라지는 홈 슬레이트 카드",
            oneOf = {
                    SlateLiveGameCard.class,
                    SlateScheduledGameCard.class,
                    SlateFinishedGameCard.class
            }
    )
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
