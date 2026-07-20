package com.pulse.api.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.domain.GameEventRepository;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.Lineup;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.ranking.RankingService;
import com.pulse.ranking.PersonalizationCalculator;
import com.pulse.ranking.LiveRankingPruner;
import com.pulse.common.user.UserPreferenceReader;
import com.pulse.common.user.UserPreferenceReader.UserPreferences;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final PersonalizationCalculator personalizationCalculator = mock(PersonalizationCalculator.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    private final HomeGameCardAssembler gameCardAssembler = new HomeGameCardAssembler(
            watchScoreRepository,
            gameEventRepository,
            lineupRepository,
            playerRepository,
            redisTemplate);
    private final HomePersonalizedSorter personalizedSorter = new HomePersonalizedSorter(
            personalizationCalculator, Optional.empty(), lineupRepository);
    private final LiveRankingPruner liveRankingPruner = new LiveRankingPruner(rankingService);
    private final HomeRankingReader homeRankingReader = new HomeRankingReader(
            gameRepository, rankingService, liveRankingPruner, personalizedSorter, gameCardAssembler);
    private final HomeSlateReader homeSlateReader = new HomeSlateReader(
            gameRepository, rankingService, personalizedSorter, gameCardAssembler);
    private final HomeQueryService service = new HomeQueryService(
            homeRankingReader, homeSlateReader, rankingCache());

    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        when(watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(anyLong())).thenReturn(Optional.empty());
        when(watchScoreRepository.findLatestByGameIdIn(any())).thenReturn(List.of());
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(gameEventRepository.findFirstByGameIdAndSpoilerLevelOrderByObservedAtDescIdDesc(
                anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(gameEventRepository.findLatestByGameIdInAndSpoilerLevel(
                any(), org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
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
        when(rankingService.topLive(1000)).thenReturn(orderedScores(1L, 2L, 3L, 4L, 5L));
        when(gameRepository.findAllById(List.of(1L, 2L, 3L, 4L, 5L))).thenReturn(liveGames);

        HomeRankingResponse response = service.getRanking(20);

        assertThat(response.live()).extracting(RankingLiveGameCard::gameId)
                .containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(response.scheduled()).isEmpty();
        assertThat(response.finished()).isEmpty();
    }

    @Test
    void getRanking_shouldNotQueryGamesAndCardDetailsOneByOne() {
        Game live = live(1L);
        Game finished = finished(10L, 90);
        when(rankingService.topLive(1000)).thenReturn(orderedScores(1L));
        when(gameRepository.findAllById(List.of(1L))).thenReturn(List.of(live));
        when(gameRepository.findByStatusAndStartTimeBetween(
                eq(Game.STATUS_SCHEDULED), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(gameRepository.findByStatusStartingWithAndStartTimeGreaterThanEqual(
                eq(Game.STATUS_FINAL), any(Instant.class)))
                .thenReturn(List.of(finished));

        service.getRanking(2);

        verify(gameRepository).findAllById(List.of(1L));
        verify(gameRepository, never()).findById(anyLong());
        verify(watchScoreRepository).findLatestByGameIdIn(List.of(1L));
        verify(gameEventRepository).findLatestByGameIdInAndSpoilerLevel(
                List.of(10L), com.pulse.domain.GameEvent.SPOILER_PROTECTED_SAFE);
        verify(watchScoreRepository, never()).findTopByGameIdOrderByComputedAtDesc(anyLong());
        verify(gameEventRepository, never())
                .findFirstByGameIdAndSpoilerLevelOrderByObservedAtDescIdDesc(
                        anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getRanking_shouldExcludeAndRemoveStaleLiveRankingMembers() {
        Game finished = finished(100L, 80);
        Game live = live(101L);
        Map<Long, Double> liveScores = new LinkedHashMap<>();
        liveScores.put(100L, 90.0);
        liveScores.put(101L, 80.0);
        liveScores.put(102L, 70.0);
        when(rankingService.topLive(1000)).thenReturn(liveScores);
        when(gameRepository.findAllById(List.of(100L, 101L, 102L))).thenReturn(List.of(finished, live));

        HomeRankingResponse response = service.getRanking(5);

        assertThat(response.live()).extracting(RankingLiveGameCard::gameId)
                .containsExactly(101L);
        verify(rankingService).removeLive(100L);
        verify(rankingService).removeLive(102L);
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

        when(rankingService.topLive(1000)).thenReturn(orderedScores(1L));
        when(gameRepository.findAllById(List.of(1L))).thenReturn(List.of(live));
        when(gameRepository.findByStatusAndStartTimeBetween(
                org.mockito.ArgumentMatchers.eq(Game.STATUS_SCHEDULED), any(Instant.class), any(Instant.class)))
                .thenReturn(scheduled);
        when(gameRepository.findByStatusStartingWithAndStartTimeGreaterThanEqual(
                org.mockito.ArgumentMatchers.eq(Game.STATUS_FINAL), any(Instant.class)))
                .thenReturn(finished);

        HomeRankingResponse response = service.getRanking(5);

        assertThat(response.live()).extracting(RankingLiveGameCard::gameId)
                .containsExactly(1L);
        assertThat(response.finished()).extracting(RankingFinishedGameCard::gameId)
                .containsExactly(10L, 11L, 12L);
        assertThat(response.scheduled()).extracting(RankingScheduledGameCard::gameId)
                .containsExactly(20L);
        assertThat(totalCards(response)).isEqualTo(5);
    }

    @Test
    void getRanking_shouldBackfillFromOutsideWindowsWhenRecentGamesMissing() {
        // 창(48h/36h) 안 경기가 없어 랭킹이 비는 상황: 창 밖 종료·예정 경기로 5개를 채워야 한다.
        List<Game> recentFinished = List.of(
                finished(10L, 90),
                finished(11L, 80),
                finished(12L, 70),
                finished(13L, 60)
        );
        List<Game> upcomingScheduled = List.of(
                scheduled(20L, 90),
                scheduled(21L, 80)
        );

        when(rankingService.topLive(1000)).thenReturn(Map.of());
        when(gameRepository.findByStatusAndStartTimeBetween(
                eq(Game.STATUS_SCHEDULED), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(gameRepository.findByStatusStartingWithAndStartTimeGreaterThanEqual(
                eq(Game.STATUS_FINAL), any(Instant.class)))
                .thenReturn(List.of());
        when(gameRepository.findByStatusStartingWith(
                eq(Game.STATUS_FINAL), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(recentFinished);
        when(gameRepository.findByStatusAndStartTimeGreaterThanEqual(
                eq(Game.STATUS_SCHEDULED), any(Instant.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(upcomingScheduled);

        HomeRankingResponse response = service.getRanking(5);

        assertThat(response.live()).isEmpty();
        assertThat(response.finished()).extracting(RankingFinishedGameCard::gameId)
                .containsExactly(10L, 11L, 12L, 13L);
        assertThat(response.scheduled()).extracting(RankingScheduledGameCard::gameId)
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

        when(rankingService.topLive(1000)).thenReturn(Map.of());
        when(gameRepository.findByStatusAndStartTimeBetween(
                org.mockito.ArgumentMatchers.eq(Game.STATUS_SCHEDULED), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(scheduled));
        when(gameRepository.findByStatusStartingWithAndStartTimeGreaterThanEqual(
                org.mockito.ArgumentMatchers.eq(Game.STATUS_FINAL), any(Instant.class)))
                .thenReturn(List.of());
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

    @Test
    void getRanking_shouldApplyPersonalizationBeforeSelectingLiveCards() {
        Game first = live(1L);
        Game favorite = live(2L);
        UserPreferenceReader preferenceReader = mock(UserPreferenceReader.class);
        HomePersonalizedSorter personalizedHomeSorter = new HomePersonalizedSorter(
                personalizationCalculator, Optional.of(preferenceReader), lineupRepository);
        HomeQueryService personalizedService = new HomeQueryService(
                new HomeRankingReader(
                        gameRepository,
                        rankingService,
                        liveRankingPruner,
                        personalizedHomeSorter,
                        gameCardAssembler),
                new HomeSlateReader(
                        gameRepository,
                        rankingService,
                        personalizedHomeSorter,
                        gameCardAssembler),
                rankingCache());

        Map<Long, Double> liveScores = new LinkedHashMap<>();
        liveScores.put(1L, 90.0);
        liveScores.put(2L, 85.0);
        when(rankingService.topLive(1000)).thenReturn(liveScores);
        when(gameRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(first, favorite));
        when(lineupRepository.findByGameIdIn(List.of(1L, 2L))).thenReturn(List.of());
        UserPreferences preferences = new UserPreferences(Set.of(20L), Set.of());
        when(preferenceReader.findByEmail("user@example.com")).thenReturn(preferences);
        when(personalizationCalculator.bonus(eq(first), anySet(), eq(preferences))).thenReturn(0);
        when(personalizationCalculator.bonus(eq(favorite), anySet(), eq(preferences))).thenReturn(10);

        HomeRankingResponse response = personalizedService.getRanking(2, "user@example.com");

        assertThat(response.live()).extracting(RankingLiveGameCard::gameId)
                .containsExactly(2L, 1L);
    }

    @Test
    void getSlate_shouldDistinguishPostponedAndCanceledStates() {
        Game postponed = gameWithStatus(30L, Game.STATUS_POSTPONED);
        Game canceled = gameWithStatus(31L, Game.STATUS_CANCELED);
        when(gameRepository.findByStartTimeGreaterThanEqualAndStartTimeLessThan(
                any(Instant.class), any(Instant.class))).thenReturn(List.of(postponed, canceled));

        HomeSlateResponse response = service.getSlate("2026-07-11", "all", "startTime");

        assertThat(response.games()).hasSize(2).allMatch(SlateScheduledGameCard.class::isInstance);
        assertThat(response.games()).extracting(SlateGameCard::gameState)
                .containsExactly("POSTPONED", "CANCELED");
    }

    @Test
    void getSlate_shouldSortAllStatusesByRecommendationScore() {
        ZoneId slateZone = ZoneId.of("America/New_York");
        LocalDate slateDate = LocalDate.now(slateZone);
        Instant slateStart = slateDate.atStartOfDay(slateZone).toInstant();
        Game live = live(50L);
        live.setStartTime(slateStart.plusSeconds(12 * 60 * 60));
        Game finished = finished(51L, 90);
        finished.setStartTime(slateStart.plusSeconds(10 * 60 * 60));
        Game scheduled = scheduled(52L, 70);
        scheduled.setStartTime(slateStart.plusSeconds(14 * 60 * 60));
        when(gameRepository.findByStartTimeGreaterThanEqualAndStartTimeLessThan(
                any(Instant.class), any(Instant.class))).thenReturn(List.of(scheduled, live, finished));
        when(rankingService.topLive(1000)).thenReturn(Map.of(50L, 80.0));

        HomeSlateResponse response = service.getSlate(slateDate.toString(), "all", "recommended");

        assertThat(response.games()).extracting(SlateGameCard::gameId)
                .containsExactly(51L, 50L, 52L);
    }

    @Test
    void getSlate_shouldReturnAllUpcomingScheduledGamesRegardlessOfSlateDate() {
        Game earlier = scheduled(40L, 70);
        earlier.setStartTime(Instant.now().plusSeconds(60 * 60));
        Game later = scheduled(41L, 90);
        later.setStartTime(Instant.now().plusSeconds(7 * 24 * 60 * 60));
        Game past = scheduled(42L, 100);
        past.setStartTime(Instant.now().minusSeconds(60));
        when(gameRepository.findByStatusAndStartTimeGreaterThanEqual(
                eq(Game.STATUS_SCHEDULED), any(Instant.class)))
                .thenReturn(List.of(later, past, earlier));

        HomeSlateResponse response = service.getSlate("2026-07-01", "scheduled", null);

        assertThat(response.games()).extracting(SlateGameCard::gameId)
                .containsExactly(40L, 41L);
    }

    @Test
    void getSlate_shouldThrowInvalidSlateDateExceptionForInvalidDate() {
        assertThatThrownBy(() -> service.getSlate("2026-02-30", "all", "startTime"))
                .isInstanceOf(InvalidSlateDateException.class)
                .hasMessage("날짜는 YYYY-MM-DD 형식이어야 합니다.");
    }

    @Test
    void getSlate_shouldUseCurrentSlateDateWhenDateIsMissing() {
        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        when(gameRepository.findByStartTimeGreaterThanEqualAndStartTimeLessThan(
                any(Instant.class), any(Instant.class))).thenReturn(List.of());

        HomeSlateResponse response = service.getSlate(null, "all", "startTime");

        assertThat(response.slateDate()).isEqualTo(today);
        assertThat(response.games()).isEmpty();
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

    private static Game gameWithStatus(long id, String status) {
        Game game = baseGame(id);
        game.setStatus(status);
        game.setStartTime(Instant.parse("2026-07-11T18:00:00Z"));
        game.setVenue("Test Ballpark");
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

    private static AnonymousHomeRankingCache rankingCache() {
        return new AnonymousHomeRankingCache(
                new HomeRankingCacheProperties(Duration.ofSeconds(3)));
    }
}
