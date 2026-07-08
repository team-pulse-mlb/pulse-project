package com.pulse.api.home;

import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.ReplaySegmentRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.ranking.RankingService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
    private final ReplaySegmentRepository replaySegmentRepository;
    private final RankingService rankingService;

    public HomeRankingResponse getRanking(int count) {
        Instant now = Instant.now();
        int safeCount = rankingLimit(count);
        List<HomeGameCard> live = rankingService.topLive(safeCount).keySet().stream()
                .map(gameRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(this::toCard)
                .toList();

        int remaining = safeCount - live.size();
        if (remaining <= 0) {
            return new HomeRankingResponse(now, live, List.of(), List.of());
        }

        List<Game> candidates = gameRepository.findAll();
        List<Game> scheduledCandidates = candidates.stream()
                .filter(game -> isScheduledForHome(game, now))
                .sorted(scheduledRankingComparator())
                .toList();
        List<Game> finishedCandidates = candidates.stream()
                .filter(game -> isFinishedForHome(game, now))
                .sorted(finishedRankingComparator())
                .toList();

        int scheduledReserve = scheduledCandidates.isEmpty() ? 0 : 1;
        int finishedLimit = Math.max(0, remaining - scheduledReserve);
        List<HomeGameCard> finished = finishedCandidates.stream()
                .limit(finishedLimit)
                .map(this::toCard)
                .toList();

        remaining -= finished.size();
        List<HomeGameCard> scheduled = scheduledCandidates.stream()
                .limit(remaining)
                .map(this::toCard)
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

        List<HomeGameCard> games = gameRepository.findAll().stream()
                .filter(game -> inSlate(game, startInclusive, endExclusive))
                .filter(game -> matchesStatus(game, normalizedStatus))
                .map(this::toCard)
                .sorted(comparator(normalizedSort, liveScores))
                .toList();

        return new HomeSlateResponse(slateDate, games);
    }

    private HomeGameCard toCard(Game game) {
        WatchScore latestScore = watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(game.getId()).orElse(null);
        List<String> tags = latestScore == null || latestScore.getTags() == null
                ? List.of()
                : latestScore.getTags();
        return new HomeGameCard(
                game.getId(),
                stateOf(game),
                new MatchupResponse(teamLabel(game.getHomeTeamName(), game.getHomeTeamAbbr()),
                        teamLabel(game.getAwayTeamName(), game.getAwayTeamAbbr())),
                game.getStartTime(),
                game.isLive() ? game.getPeriod() : null,
                tags,
                headline(game, tags),
                game.isFinal() ? replaySegmentRepository.countByGameId(game.getId()) : null
        );
    }

    private static Comparator<HomeGameCard> comparator(String sort, Map<Long, Double> liveScores) {
        if ("recommended".equals(sort)) {
            return Comparator
                    .comparing((HomeGameCard card) -> liveScores.getOrDefault(card.gameId(), -1.0)).reversed()
                    .thenComparing(HomeQueryService::startTimeOrMax);
        }
        return Comparator
                .comparing((HomeGameCard card) -> !"LIVE".equals(card.gameState()))
                .thenComparing(HomeQueryService::startTimeOrMax);
    }

    private static Instant startTimeOrMax(HomeGameCard card) {
        return card.startTime() == null ? Instant.MAX : card.startTime();
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
        return LocalDate.parse(date.trim());
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
        return "UNKNOWN";
    }

    private static String teamLabel(String name, String abbr) {
        if (abbr != null && !abbr.isBlank()) {
            return abbr;
        }
        return name;
    }

    private static String headline(Game game, List<String> tags) {
        if (game.isLive() && !tags.isEmpty()) {
            return "지금 볼 만한 흐름이 감지됐습니다.";
        }
        return null;
    }

    public record HomeRankingResponse(
            Instant generatedAt,
            List<HomeGameCard> live,
            List<HomeGameCard> scheduled,
            List<HomeGameCard> finished
    ) {
    }

    public record HomeSlateResponse(
            LocalDate slateDate,
            List<HomeGameCard> games
    ) {
    }

    public record HomeGameCard(
            long gameId,
            String gameState,
            MatchupResponse matchup,
            Instant startTime,
            Integer inning,
            List<String> tags,
            String headline,
            Long replaySegmentCount
    ) {
    }

    public record MatchupResponse(String home, String away) {
    }
}
