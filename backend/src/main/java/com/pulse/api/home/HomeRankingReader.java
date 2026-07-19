package com.pulse.api.home;

import com.pulse.common.user.UserPreferenceReader.UserPreferences;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.ranking.LiveRankingPruner;
import com.pulse.ranking.RankingService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class HomeRankingReader {

    private static final int MAX_RANKING_LOOKUP = 1000;
    private static final int SCHEDULED_LOOKAHEAD_HOURS = 36;
    private static final int FINISHED_LOOKBACK_HOURS = 48;

    private final GameRepository gameRepository;
    private final RankingService rankingService;
    private final LiveRankingPruner liveRankingPruner;
    private final HomePersonalizedSorter personalizedSorter;
    private final HomeGameCardAssembler gameCardAssembler;

    HomeRankingResponse loadRanking(int safeCount, String username) {
        Instant now = Instant.now();
        UserPreferences preferences = personalizedSorter.preferencesFor(username);
        Map<Long, Double> liveScores = rankingService.topLive(MAX_RANKING_LOOKUP);
        List<Long> rankedGameIds = List.copyOf(liveScores.keySet());
        Map<Long, Game> rankedGames = gameRepository.findAllById(rankedGameIds).stream()
                .collect(Collectors.toMap(Game::getId, Function.identity()));
        List<Game> liveCandidates = liveRankingPruner.prune(rankedGameIds, rankedGames);
        Map<Long, Set<Long>> liveLineups = personalizedSorter.lineupPlayerIdsByGame(liveCandidates, preferences);
        List<Game> selectedLive = liveCandidates.stream()
                .sorted(personalizedSorter.personalizedComparator(
                        game -> liveScores.getOrDefault(game.getId(), -1.0),
                        liveLineups,
                        preferences))
                .limit(safeCount)
                .toList();
        Map<Long, String> latestTags = gameCardAssembler.latestTagsByGame(selectedLive);
        List<RankingLiveGameCard> live = selectedLive.stream()
                .map(game -> gameCardAssembler.toRankingLiveCard(game, latestTags.get(game.getId())))
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
        Map<Long, Set<Long>> nonLiveLineups = personalizedSorter
                .lineupPlayerIdsByGame(nonLiveCandidates, preferences);
        scheduledCandidates = scheduledCandidates.stream()
                .sorted(personalizedSorter.personalizedComparator(
                        game -> HomePersonalizedSorter.scoreOrMin(game.getPregameScore()),
                        nonLiveLineups,
                        preferences))
                .toList();
        finishedCandidates = finishedCandidates.stream()
                .sorted(personalizedSorter.personalizedComparator(
                        game -> HomePersonalizedSorter.scoreOrMin(game.getPeakBaseScore()),
                        nonLiveLineups,
                        preferences))
                .toList();

        int scheduledReserve = scheduledCandidates.isEmpty() ? 0 : 1;
        int finishedLimit = Math.max(0, remaining - scheduledReserve);
        List<Game> selectedFinished = finishedCandidates.stream()
                .limit(finishedLimit)
                .toList();
        Map<Long, String> keyMoments = gameCardAssembler.keyMomentsByGame(selectedFinished);
        List<RankingFinishedGameCard> finished = selectedFinished.stream()
                .map(game -> gameCardAssembler.toRankingFinishedCard(game, keyMoments.get(game.getId())))
                .toList();

        remaining -= finished.size();
        List<Game> selectedScheduled = scheduledCandidates.stream()
                .limit(remaining)
                .toList();
        Map<Long, ProbablePitchersResponse> probablePitchers = gameCardAssembler
                .probablePitchersByGame(selectedScheduled);
        List<RankingScheduledGameCard> scheduled = selectedScheduled.stream()
                .map(game -> gameCardAssembler.toRankingScheduledCard(game, probablePitchers.get(game.getId())))
                .toList();

        return new HomeRankingResponse(now, live, scheduled, finished);
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
}
