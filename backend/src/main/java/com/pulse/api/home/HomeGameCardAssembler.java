package com.pulse.api.home;

import com.pulse.domain.Game;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventLabelPolicy;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.Lineup;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class HomeGameCardAssembler {

    private final WatchScoreRepository watchScoreRepository;
    private final GameEventRepository gameEventRepository;
    private final LineupRepository lineupRepository;
    private final PlayerRepository playerRepository;
    private final StringRedisTemplate redisTemplate;

    RankingLiveGameCard toRankingLiveCard(Game game, String latestTag) {
        return new RankingLiveGameCard(
                game.getId(),
                matchup(game),
                game.getPeriod(),
                latestTag
        );
    }

    RankingScheduledGameCard toRankingScheduledCard(
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

    RankingFinishedGameCard toRankingFinishedCard(Game game, String keyMoment) {
        return new RankingFinishedGameCard(
                game.getId(),
                matchup(game),
                headline(game),
                keyMoment
        );
    }

    SlateGameCard toSlateCard(Game game, ProbablePitchersResponse probablePitchers) {
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

    Map<Long, String> latestTagsByGame(List<Game> games) {
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

    Map<Long, String> keyMomentsByGame(List<Game> games) {
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

    Map<Long, ProbablePitchersResponse> probablePitchersByGame(List<Game> games) {
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

    static String playerName(Player player) {
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

    MatchupResponse matchup(Game game) {
        return new MatchupResponse(
                teamLabel(game.getHomeTeamName(), game.getHomeTeamAbbr()),
                teamLabel(game.getAwayTeamName(), game.getAwayTeamAbbr())
        );
    }

    static String teamLabel(String name, String abbr) {
        if (abbr != null && !abbr.isBlank()) {
            return abbr;
        }
        return name;
    }

    static String stateOf(Game game) {
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

    static String headline(Game game) {
        if (game.isFinal()
                && game.getFinalHeadlineProtected() != null
                && !game.getFinalHeadlineProtected().isBlank()) {
            return game.getFinalHeadlineProtected();
        }
        return null;
    }

    String latestTag(Game game) {
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

    String keyMoment(Game game) {
        if (!game.isFinal()) {
            return null;
        }
        return gameEventRepository
                .findFirstByGameIdAndSpoilerLevelOrderByObservedAtDescIdDesc(
                        game.getId(), GameEvent.SPOILER_PROTECTED_SAFE)
                .map(event -> GameEventLabelPolicy.protectedLabel(event.getSpoilerLevel(), event.getEventType()))
                .orElse(null);
    }

    static String liveCacheKey(long gameId) {
        return "game:" + gameId + ":live";
    }

    static String valueOf(Object value) {
        return value == null ? null : value.toString();
    }
}
