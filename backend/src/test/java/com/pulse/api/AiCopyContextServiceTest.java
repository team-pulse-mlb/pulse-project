package com.pulse.api;

import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.FinalHeadlineContext;
import com.pulse.common.ai.RevealedEventCopyContext;
import com.pulse.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiCopyContextServiceTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final GameEventRepository eventRepository = mock(GameEventRepository.class);
    private final WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
    private final PlayerRepository playerRepository = mock(PlayerRepository.class);
    private final PlayRepository playRepository = mock(PlayRepository.class);
    private final AiCopyContextService service = new AiCopyContextService(
            gameRepository, eventRepository, watchScoreRepository, playerRepository, playRepository);

    @BeforeEach
    void 기본값을_설정한다() {
        // PROTECTED 헤드라인에서 사용할 보호 이벤트 기본값입니다.
        when(eventRepository.findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                1L,
                GameEvent.SPOILER_PROTECTED_SAFE
        )).thenReturn(List.of());

        // watch score가 없는 테스트에서도 보호 컨텍스트가 정상 생성되도록 합니다.
        when(watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(1L))
                .thenReturn(Optional.empty());

        // REVEALED 컨텍스트가 경기 전체 play를 조회하므로,
        // 각 테스트가 별도 play fixture를 설정하지 않은 경우 빈 목록을 반환합니다.
        when(playRepository.findByGameIdOrderByPlayOrderAsc(1L))
                .thenReturn(List.of());

        // REVEALED 컨텍스트의 공개 이벤트 조회 기본값입니다.
        // Mockito의 기본 null 반환으로 revealedEvents/revealedMoments에서
        // NullPointerException이 발생하지 않도록 빈 목록을 설정합니다.
        when(eventRepository
                .findByGameIdAndSpoilerLevelAndEventTypeInOrderByInningAscSourceRefAscIdAsc(
                        1L,
                        GameEvent.SPOILER_REVEALED_ONLY,
                        List.of(
                                "scoring_play",
                                "home_run",
                                "lead_change",
                                "big_inning"
                        )
                ))
                .thenReturn(List.of());
    }

    @Test
    void keyMoments는_중복과_쿼터를_적용하고_최신_우선_선별_후_시간순으로_정렬한다() {
        List<GameEvent> events = new ArrayList<>();
        events.add(event(1, 1, "pressure_bases_loaded", 1));
        events.add(event(2, 1, "pressure_bases_loaded", 2));
        events.add(event(3, 1, "hard_contact", 3));
        events.add(event(4, 1, "long_at_bat", 4));
        events.add(event(5, 2, "pressure_bases_loaded", 5));
        events.add(event(6, 3, "pressure_bases_loaded", 6));
        events.add(event(7, 4, "hard_contact", 7));
        events.add(event(8, 5, "long_at_bat", 8));
        events.add(event(9, 6, "full_count_two_out", 9));
        events.add(event(10, 7, "pitcher_instability", 10));
        events.add(event(11, 8, "pressure_scoring_position", 11));
        events.add(event(12, 9, "unknown", 12));

        List<FinalHeadlineContext.KeyMoment> result = AiCopyContextService.selectKeyMoments(events);

        assertThat(result).hasSize(8);
        assertThat(result).doesNotContain(new FinalHeadlineContext.KeyMoment(1, "만루 승부"));
        assertThat(result).extracting(FinalHeadlineContext.KeyMoment::inning)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(result).filteredOn(moment -> moment.label().equals("만루 승부")).hasSize(2);
    }

    @Test
    void evidence는_유형별_사실_수치만_남긴다() {
        assertThat(AiCopyContextService.projectEvidence("pressure_bases_loaded", Map.of(
                "outs", 2, "balls", 3, "strikes", 2, "runnerOnFirst", true, "text", "득점")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("outs", 2, "balls", 3, "strikes", 2));
        assertThat(AiCopyContextService.projectEvidence("pitcher_instability", Map.of(
                "pitcherPitchCount", 95, "velocityDropMph", 2.1, "result", "교체")))
                .containsExactlyInAnyOrderEntriesOf(Map.of("pitcherPitchCount", 95, "velocityDropMph", 2.1));
        assertThat(AiCopyContextService.projectEvidence("lead_change", Map.of("scoreValue", 1))).isEmpty();
    }

    @Test
    void 보호_evidence는_상황만_남기고_결과_암시값을_제외한다() {
        assertThat(AiCopyContextService.projectProtectedEvidence("full_count_two_out", Map.of(
                "outs", 2, "balls", 3, "strikes", 2,
                "runnerOnFirst", true, "runnerOnSecond", false, "runnerOnThird", true)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "outs", 2, "balls", 3, "strikes", 2,
                        "runnerOnFirst", true, "runnerOnSecond", false, "runnerOnThird", true));
        // 투수 흔들림은 누적 투구수만, 구속 저하(결과 암시)는 제외
        assertThat(AiCopyContextService.projectProtectedEvidence("pitcher_instability", Map.of(
                "pitcherPitchCount", 102, "velocityDropMph", 2.4)))
                .containsExactlyInAnyOrderEntriesOf(Map.of("pitcherPitchCount", 102));
        // 강한 타구는 아웃카운트·주자 상황만 남기고 타구질(결과 암시)은 제외한다
        assertThat(AiCopyContextService.projectProtectedEvidence("hard_contact", Map.of(
                "isBarrel", true, "exitVelocity", 112,
                "outs", 1, "runnerOnFirst", true, "runnerOnSecond", false, "runnerOnThird", false)))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "outs", 1, "runnerOnFirst", true, "runnerOnSecond", false, "runnerOnThird", false));
    }

    @Test
    void 이벤트_컨텍스트의_차단_조건을_적용한다() {
        when(eventRepository.findById(10L)).thenReturn(Optional.empty());
        assertThat(service.eventCopyContext(1L, 10L, AiCopyMode.PROTECTED)).isEmpty();

        GameEvent mismatch = event(10, 1, "hard_contact", 1);
        mismatch.setGameId(2L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(mismatch));
        assertThat(service.eventCopyContext(1L, 10L, AiCopyMode.PROTECTED)).isEmpty();

        GameEvent unknownLevel = event(11, 1, "hard_contact", 1);
        unknownLevel.setSpoilerLevel("UNKNOWN");
        when(eventRepository.findById(11L)).thenReturn(Optional.of(unknownLevel));
        assertThat(service.eventCopyContext(1L, 11L, AiCopyMode.REVEALED)).isEmpty();

        GameEvent revealedOnly = event(12, 1, "home_run", 1);
        revealedOnly.setSpoilerLevel(GameEvent.SPOILER_REVEALED_ONLY);
        when(eventRepository.findById(12L)).thenReturn(Optional.of(revealedOnly));
        assertThat(service.eventCopyContext(1L, 12L, AiCopyMode.PROTECTED)).isEmpty();

        GameEvent unknownType = event(13, 1, "unknown", 1);
        when(eventRepository.findById(13L)).thenReturn(Optional.of(unknownType));
        assertThat(service.eventCopyContext(1L, 13L, AiCopyMode.REVEALED)).isEmpty();
    }

    @Test
    void 공개_이벤트만_추가_필드와_화이트리스트_evidence를_포함한다() {
        GameEvent event = event(20, 8, "home_run", 20);
        event.setSpoilerLevel(GameEvent.SPOILER_REVEALED_ONLY);
        event.setInningType("TOP");
        event.setPayload(Map.of("scoreValue", 2, "text", "홈런"));
        when(eventRepository.findById(20L)).thenReturn(Optional.of(event));

        RevealedEventCopyContext context = (RevealedEventCopyContext) service
                .eventCopyContext(1L, 20L, AiCopyMode.REVEALED).orElseThrow();

        assertThat(context.inningType()).isEqualTo("TOP");
        assertThat(context.batter()).isNull();
        assertThat(context.pitcher()).isNull();
        assertThat(context.evidence()).containsOnlyKeys("scoreValue");
    }

    @Test
    void 경기_없음과_미종료_경기는_헤드라인_컨텍스트를_반환하지_않는다() {
        when(gameRepository.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.finalHeadlineContext(1L, AiCopyMode.PROTECTED)).isEmpty();

        Game live = game(Game.STATUS_IN_PROGRESS, 2, 1);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(live));
        assertThat(service.finalHeadlineContext(1L, AiCopyMode.PROTECTED)).isEmpty();
    }

    @Test
    void 최종_점수와_승자는_공개_헤드라인에만_포함한다() {
        Game game = game(Game.STATUS_FINAL, 5, 3);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        FinalHeadlineContext protectedContext = service
                .finalHeadlineContext(1L, AiCopyMode.PROTECTED).orElseThrow();
        FinalHeadlineContext revealedContext = service
                .finalHeadlineContext(1L, AiCopyMode.REVEALED).orElseThrow();

        assertThat(protectedContext.finalScore()).isNull();
        assertThat(protectedContext.winner()).isNull();
        assertThat(revealedContext.finalScore()).isEqualTo(new FinalHeadlineContext.FinalScore(5, 3));
        assertThat(revealedContext.winner()).isEqualTo("home");
        assertThat(revealedContext.contextHash()).isNotEqualTo(protectedContext.contextHash());
    }

    @Test
    void 공개_헤드라인은_결과_컨텍스트와_대표_득점_장면만_일괄_조회한다() {
        Game game = game(Game.STATUS_FINAL, 5, 3);
        game.setHomeTeamName("Los Angeles Dodgers");
        game.setHomeTeamAbbr("LAD");
        game.setAwayTeamName("San Francisco Giants");
        game.setAwayTeamAbbr("SF");
        game.setPeriod(10);
        game.setPostseason(false);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        GameEvent bigInning = revealedEvent(30, 7, "Top", "big_inning", 30L, 301L,
                Map.of("scoringPlays", 3L));
        GameEvent scoring = revealedEvent(10, 8, "Top", "scoring_play", 10L, 101L,
                Map.of("scoreValue", 2));
        GameEvent homeRun = revealedEvent(11, 8, "Top", "home_run", 10L, 101L,
                Map.of("scoreValue", 2));
        GameEvent leadChange = revealedEvent(12, 8, "Top", "lead_change", 10L, 101L, Map.of());
        GameEvent lastScoring = revealedEvent(20, 9, "Bottom", "scoring_play", 20L, null,
                Map.of("scoreValue", 1));
        when(eventRepository.findByGameIdAndSpoilerLevelAndEventTypeInOrderByInningAscSourceRefAscIdAsc(
                1L, GameEvent.SPOILER_REVEALED_ONLY,
                List.of("scoring_play", "home_run", "lead_change", "big_inning")))
                .thenReturn(List.of(bigInning, scoring, homeRun, leadChange, lastScoring));
        when(playRepository.findByGameIdAndPlayOrderIn(1L, List.of(30L, 10L, 20L)))
                .thenReturn(List.of(play(10L, 2, 3), play(20L, 3, 3), play(30L, 1, 0)));
        when(playerRepository.findAllById(List.of(301L, 101L)))
                .thenReturn(List.of(player(101L, "Heliot Ramos"), player(301L, "Big Inning Batter")));

        FinalHeadlineContext context = service
                .finalHeadlineContext(1L, AiCopyMode.REVEALED).orElseThrow();

        assertThat(context.reasonTags()).isEmpty();
        assertThat(context.spoilerSafeSignals()).isEmpty();
        assertThat(context.keyMoments()).isEmpty();
        assertThat(context.teams()).isEqualTo(new FinalHeadlineContext.Teams(
                new FinalHeadlineContext.Team("Los Angeles Dodgers", "LAD"),
                new FinalHeadlineContext.Team("San Francisco Giants", "SF")));
        assertThat(context.inningsPlayed()).isEqualTo(10);
        assertThat(context.extraInnings()).isTrue();
        assertThat(context.postseason()).isFalse();
        assertThat(context.revealedMoments()).extracting(FinalHeadlineContext.RevealedMoment::inning)
                .containsExactly(7, 8, 9);
        assertThat(context.revealedMoments().get(1).eventTypes())
                .containsExactly("scoring_play", "home_run", "lead_change");
        assertThat(context.revealedMoments().get(1).battingTeam()).isEqualTo("SF");
        assertThat(context.revealedMoments().get(1).batter()).isEqualTo("Heliot Ramos");
        assertThat(context.revealedMoments().get(1).runsScored()).isEqualTo(2);
        assertThat(context.revealedMoments().get(1).scoreAfter())
                .isEqualTo(new FinalHeadlineContext.ScoreAfter(2, 3));
        verify(watchScoreRepository, never()).findTopByGameIdOrderByComputedAtDesc(1L);
        verify(eventRepository, never()).findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                1L, GameEvent.SPOILER_PROTECTED_SAFE);
    }

    @Test
    void 공개_헤드라인은_점수_진행에서_고급_summaryFacts와_핵심_플레이_태그를_계산한다() {
        Game game = game(Game.STATUS_FINAL, 3, 2);
        game.setHomeTeamName("San Francisco Giants");
        game.setAwayTeamName("Colorado Rockies");
        game.setPeriod(9);
        game.setHomeInningScores(List.of(0, 0, 0, 1, 0, 0, 0, 0, 2));
        game.setAwayInningScores(List.of(1, 0, 0, 0, 0, 0, 0, 1, 0));
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));

        List<Play> plays = List.of(
                scoringPlay(101L, 101L, 1, "Top", 0, 1, 1),
                scoringPlay(102L, 102L, 4, "Bottom", 1, 1, 1),
                scoringPlay(103L, 103L, 8, "Top", 1, 2, 1),
                scoringPlay(104L, 104L, 9, "Bottom", 3, 2, 2)
        );
        when(playRepository.findByGameIdOrderByPlayOrderAsc(1L)).thenReturn(plays);

        FinalHeadlineContext context = service
                .finalHeadlineContext(1L, AiCopyMode.REVEALED)
                .orElseThrow();

        FinalHeadlineContext.SummaryFacts facts = context.summaryFacts();
        assertThat(facts.winnerSide()).isEqualTo("home");
        assertThat(facts.winnerName()).isEqualTo("San Francisco Giants");
        assertThat(facts.loserName()).isEqualTo("Colorado Rockies");
        assertThat(facts.winnerScore()).isEqualTo(3);
        assertThat(facts.loserScore()).isEqualTo(2);
        assertThat(facts.firstScoringSide()).isEqualTo("away");
        assertThat(facts.firstScoringInning()).isEqualTo(1);
        assertThat(facts.tyingInning()).isEqualTo(4);
        assertThat(facts.decisiveInning()).isEqualTo(9);
        assertThat(facts.decisiveRuns()).isEqualTo(2);
        assertThat(facts.leadChangeCount()).isEqualTo(1);
        assertThat(facts.comebackWin()).isTrue();
        assertThat(facts.walkOff()).isTrue();
        assertThat(facts.shutout()).isFalse();
        assertThat(facts.extraInnings()).isFalse();
        assertThat(facts.finalInning()).isEqualTo(9);
        assertThat(facts.scoreGap()).isEqualTo(1);
        assertThat(facts.totalRuns()).isEqualTo(5);

        assertThat(context.verifiedPlays())
                .extracting(FinalHeadlineContext.VerifiedPlay::playOrder)
                .containsExactly(101L, 102L, 103L, 104L);
        assertThat(context.verifiedPlays())
                .filteredOn(play -> play.playOrder().equals(101L))
                .singleElement()
                .satisfies(play -> assertThat(play.factTags()).contains("FIRST_SCORE"));
        assertThat(context.verifiedPlays())
                .filteredOn(play -> play.playOrder().equals(102L))
                .singleElement()
                .satisfies(play -> assertThat(play.factTags()).contains("TYING_SCORE"));
        assertThat(context.verifiedPlays())
                .filteredOn(play -> play.playOrder().equals(104L))
                .singleElement()
                .satisfies(play -> assertThat(play.factTags())
                        .contains("LEAD_CHANGE", "DECISIVE_SCORE", "COMEBACK_WIN", "WALK_OFF"));
    }

    @Test
    void 공개_헤드라인은_추가_경기_흐름_표현용_플레이_태그를_계산한다() {
        Game game = game(Game.STATUS_FINAL, 5, 3);
        game.setHomeTeamName("San Francisco Giants");
        game.setAwayTeamName("Colorado Rockies");
        game.setPeriod(9);
        game.setHomeInningScores(
                List.of(0, 0, 0, 1, 0, 0, 1, 3, 0)
        );
        game.setAwayInningScores(
                List.of(1, 0, 0, 0, 0, 2, 0, 0, 0)
        );

        when(gameRepository.findById(1L))
                .thenReturn(Optional.of(game));

        List<Play> plays = new ArrayList<>(
                List.of(
                        scoringPlay(
                                101L,
                                101L,
                                1,
                                "Top",
                                0,
                                1,
                                1
                        ),
                        scoringPlay(
                                102L,
                                102L,
                                4,
                                "Bottom",
                                1,
                                1,
                                1
                        ),
                        scoringPlay(
                                103L,
                                103L,
                                6,
                                "Top",
                                1,
                                3,
                                2
                        ),
                        scoringPlay(
                                104L,
                                104L,
                                7,
                                "Bottom",
                                2,
                                3,
                                1
                        ),
                        scoringPlay(
                                105L,
                                105L,
                                8,
                                "Bottom",
                                4,
                                3,
                                2
                        ),
                        scoringPlay(
                                106L,
                                106L,
                                8,
                                "Bottom",
                                5,
                                3,
                                1
                        )
                )
        );

        plays.get(1).setText(
                "Tying Batter singled to center."
        );
        plays.get(4).setTextKo(
                "Go Ahead Batter, 좌익수 방면 안타로 2득점."
        );
        plays.get(5).setText(
                "Insurance Batter doubled to left."
        );

        when(
                playRepository.findByGameIdOrderByPlayOrderAsc(1L)
        ).thenReturn(plays);

        FinalHeadlineContext context = service
                .finalHeadlineContext(
                        1L,
                        AiCopyMode.REVEALED
                )
                .orElseThrow();

        assertThat(context.verifiedPlays())
                .filteredOn(
                        play -> play.playOrder().equals(102L)
                )
                .singleElement()
                .satisfies(
                        play -> assertThat(play.factTags())
                                .contains(
                                        "TYING_SCORE",
                                        "HIT"
                                )
                );

        assertThat(context.verifiedPlays())
                .filteredOn(
                        play -> play.playOrder().equals(104L)
                )
                .singleElement()
                .satisfies(
                        play -> assertThat(play.factTags())
                                .contains(
                                        "TRAILS_AFTER",
                                        "CUTS_DEFICIT"
                                )
                );

        assertThat(context.verifiedPlays())
                .filteredOn(
                        play -> play.playOrder().equals(105L)
                )
                .singleElement()
                .satisfies(
                        play -> assertThat(play.factTags())
                                .contains(
                                        "LEAD_CHANGE",
                                        "TAKES_LEAD",
                                        "LEADS_AFTER",
                                        "DECISIVE_SCORE",
                                        "COMEBACK_WIN",
                                        "HIT"
                                )
                );

        assertThat(context.verifiedPlays())
                .filteredOn(
                        play -> play.playOrder().equals(106L)
                )
                .singleElement()
                .satisfies(
                        play -> assertThat(play.factTags())
                                .contains(
                                        "LEADS_AFTER",
                                        "INSURANCE_SCORE",
                                        "HIT"
                                )
                );
    }

    @Test
    void 플레이_점수_흐름이_최종_점수와_다르면_고급_사실을_추측하지_않는다() {
        Game game = game(Game.STATUS_FINAL, 5, 3);
        game.setPeriod(9);
        when(gameRepository.findById(1L)).thenReturn(Optional.of(game));
        when(playRepository.findByGameIdOrderByPlayOrderAsc(1L)).thenReturn(List.of(
                scoringPlay(201L, 201L, 1, "Top", 0, 1, 1),
                scoringPlay(202L, 202L, 4, "Bottom", 2, 1, 2)
        ));

        FinalHeadlineContext.SummaryFacts facts = service
                .finalHeadlineContext(1L, AiCopyMode.REVEALED)
                .orElseThrow()
                .summaryFacts();

        assertThat(facts.firstScoringSide()).isNull();
        assertThat(facts.firstScoringInning()).isNull();
        assertThat(facts.tyingInning()).isNull();
        assertThat(facts.decisiveInning()).isNull();
        assertThat(facts.decisiveRuns()).isNull();
        assertThat(facts.leadChangeCount()).isNull();
        assertThat(facts.comebackWin()).isNull();
        assertThat(facts.walkOff()).isNull();
        assertThat(facts.shutout()).isFalse();
        assertThat(facts.scoreGap()).isEqualTo(2);
        assertThat(facts.totalRuns()).isEqualTo(8);
    }

    private static GameEvent event(long id, int inning, String eventType, long second) {
        GameEvent event = new GameEvent();
        event.setId(id);
        event.setGameId(1L);
        event.setInning(inning);
        event.setEventType(eventType);
        event.setSpoilerLevel(GameEvent.SPOILER_PROTECTED_SAFE);
        event.setObservedAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(second));
        return event;
    }

    private static GameEvent revealedEvent(
            long id,
            int inning,
            String inningType,
            String eventType,
            long sourceRef,
            Long batterId,
            Map<String, Object> payload
    ) {
        GameEvent event = event(id, inning, eventType, id);
        event.setSpoilerLevel(GameEvent.SPOILER_REVEALED_ONLY);
        event.setSourceType(GameEvent.SOURCE_TYPE_PLAY);
        event.setSourceRef(sourceRef);
        event.setInningType(inningType);
        event.setBatterId(batterId);
        event.setPayload(payload);
        return event;
    }

    private static Play scoringPlay(
            long id,
            long playOrder,
            int inning,
            String inningType,
            int homeScore,
            int awayScore,
            int scoreValue
    ) {
        Play play = new Play();
        play.setId(id);
        play.setGameId(1L);
        play.setPlayOrder(playOrder);
        play.setType("Play Result");
        play.setInning(inning);
        play.setInningType(inningType);
        play.setText("score change " + playOrder);
        play.setHomeScore(homeScore);
        play.setAwayScore(awayScore);
        play.setScoringPlay(true);
        play.setScoreValue(scoreValue);
        return play;
    }

    private static Play play(long playOrder, int homeScore, int awayScore) {
        Play play = new Play();
        play.setGameId(1L);
        play.setPlayOrder(playOrder);
        play.setHomeScore(homeScore);
        play.setAwayScore(awayScore);
        return play;
    }

    private static Player player(long id, String fullName) {
        Player player = new Player();
        player.setId(id);
        player.setFullName(fullName);
        return player;
    }

    private static Game game(String status, int homeRuns, int awayRuns) {
        Game game = new Game();
        game.setId(1L);
        game.setStatus(status);
        game.setHomeRuns(homeRuns);
        game.setAwayRuns(awayRuns);
        return game;
    }
}
