package com.pulse.api.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pulse.api.home.HomeQueryService.HomeRankingResponse;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.Lineup;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.ranking.RankingService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class HomeQueryServiceTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
    private final GameEventRepository gameEventRepository = mock(GameEventRepository.class);
    private final LineupRepository lineupRepository = mock(LineupRepository.class);
    private final PlayerRepository playerRepository = mock(PlayerRepository.class);
    private final RankingService rankingService = mock(RankingService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    private final HomeQueryService service = new HomeQueryService(
            gameRepository,
            watchScoreRepository,
            gameEventRepository,
            lineupRepository,
            playerRepository,
            rankingService,
            redisTemplate
    );

    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        when(watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(anyLong())).thenReturn(Optional.empty());
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(gameEventRepository.findFirstByGameIdAndSpoilerLevelOrderByObservedAtDescIdDesc(
                anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
    }

    @Test
    void getRanking_shouldCapLiveCardsAtFive() {
        List<Game> liveGames = List.of(
                live(1L),
                live(2L),
                live(3L),
                live(4L),
                live(5L)
        );
        when(rankingService.topLive(5)).thenReturn(orderedScores(1L, 2L, 3L, 4L, 5L));
        liveGames.forEach(game -> when(gameRepository.findById(game.getId())).thenReturn(Optional.of(game)));

        HomeRankingResponse response = service.getRanking(20);

        assertThat(response.live()).extracting(HomeQueryService.RankingLiveGameCard::gameId)
                .containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(response.scheduled()).isEmpty();
        assertThat(response.finished()).isEmpty();
    }

    @Test
    void getRanking_shouldFillFiveCardsByLiveFinishedThenScheduled() {
        Game live = live(1L);
        List<Game> finished = List.of(
                finished(10L, 90),
                finished(11L, 80),
                finished(12L, 70),
                finished(13L, 60)
        );
        List<Game> scheduled = List.of(
                scheduled(20L, 90),
                scheduled(21L, 80)
        );

        when(rankingService.topLive(5)).thenReturn(orderedScores(1L));
        when(gameRepository.findById(1L)).thenReturn(Optional.of(live));
        when(gameRepository.findAll()).thenReturn(concat(live, finished, scheduled));

        HomeRankingResponse response = service.getRanking(5);

        assertThat(response.live()).extracting(HomeQueryService.RankingLiveGameCard::gameId)
                .containsExactly(1L);
        assertThat(response.finished()).extracting(HomeQueryService.RankingFinishedGameCard::gameId)
                .containsExactly(10L, 11L, 12L);
        assertThat(response.scheduled()).extracting(HomeQueryService.RankingScheduledGameCard::gameId)
                .containsExactly(20L);
        assertThat(totalCards(response)).isEqualTo(5);
    }

    @Test
    void getRanking_shouldResolveProbablePitcherNamesFromLineups() {
        Game scheduled = scheduled(20L, 90);
        scheduled.setHomeTeamId(1L);
        scheduled.setAwayTeamId(2L);
        Lineup homePitcher = probablePitcher(20L, 101L, 1L);
        Lineup awayPitcher = probablePitcher(20L, 202L, 2L);
        Player homePlayer = player(101L, "Home Starter");
        Player awayPlayer = player(202L, "Away Starter");

        when(rankingService.topLive(5)).thenReturn(Map.of());
        when(gameRepository.findAll()).thenReturn(List.of(scheduled));
        when(lineupRepository.findByGameIdInAndIsProbablePitcherTrue(List.of(20L)))
                .thenReturn(List.of(homePitcher, awayPitcher));
        when(playerRepository.findAllById(List.of(101L, 202L)))
                .thenReturn(List.of(homePlayer, awayPlayer));

        HomeRankingResponse response = service.getRanking(5);

        assertThat(response.scheduled()).singleElement().satisfies(card -> {
            assertThat(card.probablePitchers().home()).isEqualTo("Home Starter");
            assertThat(card.probablePitchers().away()).isEqualTo("Away Starter");
        });
    }

    private static int totalCards(HomeRankingResponse response) {
        return response.live().size() + response.finished().size() + response.scheduled().size();
    }

    private static Map<Long, Double> orderedScores(Long... gameIds) {
        Map<Long, Double> scores = new LinkedHashMap<>();
        for (int i = 0; i < gameIds.length; i++) {
            scores.put(gameIds[i], 100.0 - i);
        }
        return scores;
    }

    private List<Game> concat(Game live, List<Game> finished, List<Game> scheduled) {
        java.util.ArrayList<Game> games = new java.util.ArrayList<>();
        games.add(live);
        games.addAll(finished);
        games.addAll(scheduled);
        return games;
    }

    private Game live(long id) {
        Game game = baseGame(id);
        game.setStatus(Game.STATUS_IN_PROGRESS);
        game.setPeriod(7);
        game.setStartTime(now.minusSeconds(60 * 60));
        return game;
    }

    private Game finished(long id, int peakBaseScore) {
        Game game = baseGame(id);
        game.setStatus(Game.STATUS_FINAL);
        game.setPeakBaseScore(peakBaseScore);
        game.setStartTime(now.minusSeconds(2 * 60 * 60));
        return game;
    }

    private Game scheduled(long id, int pregameScore) {
        Game game = baseGame(id);
        game.setStatus(Game.STATUS_SCHEDULED);
        game.setPregameScore(pregameScore);
        game.setStartTime(now.plusSeconds(2 * 60 * 60));
        return game;
    }

    private static Game baseGame(long id) {
        Game game = new Game();
        game.setId(id);
        game.setHomeTeamAbbr("H" + id);
        game.setAwayTeamAbbr("A" + id);
        return game;
    }

    private static Lineup probablePitcher(long gameId, long playerId, long teamId) {
        Lineup lineup = new Lineup();
        lineup.setGameId(gameId);
        lineup.setPlayerId(playerId);
        lineup.setTeamId(teamId);
        lineup.setIsProbablePitcher(true);
        return lineup;
    }

    private static Player player(long id, String fullName) {
        Player player = new Player();
        player.setId(id);
        player.setFullName(fullName);
        return player;
    }
}
