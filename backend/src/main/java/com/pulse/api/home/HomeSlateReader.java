package com.pulse.api.home;

import com.pulse.common.time.SlateZone;
import com.pulse.common.user.UserPreferenceReader.UserPreferences;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.ranking.RankingService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class HomeSlateReader {

    private static final ZoneId SLATE_ZONE = SlateZone.ID;
    private static final int MAX_RANKING_LOOKUP = 1000;

    private final GameRepository gameRepository;
    private final RankingService rankingService;
    private final HomePersonalizedSorter personalizedSorter;
    private final HomeGameCardAssembler gameCardAssembler;

    HomeSlateResponse getSlate(String date, String status, String sort, String username) {
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
        UserPreferences preferences = personalizedSorter.preferencesFor(username);
        Map<Long, Set<Long>> lineupPlayerIds = personalizedSorter.lineupPlayerIdsByGame(candidates, preferences);
        List<Game> selectedGames = candidates.stream()
                .sorted(personalizedSorter.gameComparator(
                        normalizedSort, liveScores, lineupPlayerIds, preferences))
                .toList();
        Map<Long, ProbablePitchersResponse> probablePitchers = gameCardAssembler
                .probablePitchersByGame(selectedGames);
        List<SlateGameCard> games = selectedGames.stream()
                .map(game -> gameCardAssembler.toSlateCard(game, probablePitchers.get(game.getId())))
                .toList();

        return new HomeSlateResponse(slateDate, games);
    }

    static LocalDate parseSlateDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(SLATE_ZONE);
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException exception) {
            throw new InvalidSlateDateException("날짜는 YYYY-MM-DD 형식이어야 합니다.", exception);
        }
    }

    static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "all";
        }
        String normalized = status.trim().toLowerCase();
        return switch (normalized) {
            case "scheduled", "live", "finished" -> normalized;
            default -> "all";
        };
    }

    static String normalizeSort(String sort, String status) {
        if (sort == null || sort.isBlank()) {
            return "scheduled".equals(status) ? "startTime" : "recommended";
        }
        String normalized = sort.trim().toLowerCase();
        if ("recommended".equals(normalized)) {
            return normalized;
        }
        return "startTime";
    }

    static boolean inSlate(Game game, Instant startInclusive, Instant endExclusive) {
        Instant startTime = game.getStartTime();
        return startTime != null && !startTime.isBefore(startInclusive) && startTime.isBefore(endExclusive);
    }

    static boolean matchesStatus(Game game, String status) {
        return switch (status) {
            case "scheduled" -> Game.STATUS_SCHEDULED.equals(game.getStatus());
            case "live" -> game.isLive();
            case "finished" -> game.isFinal();
            default -> true;
        };
    }

    static boolean isUpcomingScheduled(Game game, Instant now) {
        Instant startTime = game.getStartTime();
        return Game.STATUS_SCHEDULED.equals(game.getStatus())
                && startTime != null
                && !startTime.isBefore(now);
    }
}
