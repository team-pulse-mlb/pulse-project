package com.pulse.api.home;

import com.pulse.common.user.UserPreferenceReader;
import com.pulse.common.user.UserPreferenceReader.UserPreferences;
import com.pulse.domain.Game;
import com.pulse.domain.Lineup;
import com.pulse.domain.LineupRepository;
import com.pulse.ranking.PersonalizationCalculator;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class HomePersonalizedSorter {

    private final PersonalizationCalculator personalizationCalculator;
    private final Optional<UserPreferenceReader> userPreferenceReader;
    private final LineupRepository lineupRepository;

    Comparator<Game> personalizedComparator(
            ToDoubleFunction<Game> baseScore,
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
                .thenComparing(HomePersonalizedSorter::startTimeOrMax);
    }

    Comparator<Game> gameComparator(
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
                .thenComparing(HomePersonalizedSorter::startTimeOrMax);
    }

    static double recommendedScore(Game game, Map<Long, Double> liveScores) {
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

    static Instant startTimeOrMax(Game game) {
        return game.getStartTime() == null ? Instant.MAX : game.getStartTime();
    }

    static int scoreOrMin(Integer score) {
        return score == null ? Integer.MIN_VALUE : score;
    }

    UserPreferences preferencesFor(String username) {
        if (username == null || username.isBlank()) {
            return UserPreferences.empty();
        }
        return userPreferenceReader
                .map(reader -> reader.findByEmail(username))
                .orElseGet(UserPreferences::empty);
    }

    Map<Long, Set<Long>> lineupPlayerIdsByGame(
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
}
