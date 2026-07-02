package com.pulse.common.config;

import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.scorer.RankingService;
import com.pulse.scorer.ReasonTags;
import com.pulse.scorer.ScoreCalculator;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevGameSimulator {

    private static final long FIRST_SIMULATED_PLAY_ORDER = 1100L;
    private static final List<SimulatedPlay> PLAYS = List.of(
            new SimulatedPlay(8, "BOTTOM", "Walk after a full-count battle", 0, 0, false, 1, 3, 2),
            new SimulatedPlay(8, "BOTTOM", "Single to right field, tying run moves into scoring position", 0, 0, false, 1, 1, 1),
            new SimulatedPlay(8, "BOTTOM", "Double to left field, runner scores", 1, 0, true, 1, 0, 1),
            new SimulatedPlay(9, "TOP", "Leadoff single up the middle", 0, 0, false, 0, 1, 0),
            new SimulatedPlay(9, "TOP", "Two-run home run to center field", 0, 2, true, 0, 0, 0),
            new SimulatedPlay(9, "BOTTOM", "Two-out full-count lineout to end the inning", 0, 0, false, 2, 3, 2),
            new SimulatedPlay(10, "TOP", "Extra-inning sacrifice fly brings in the automatic runner", 0, 1, true, 1, 0, 1),
            new SimulatedPlay(10, "BOTTOM", "RBI single ties the game again", 1, 0, true, 1, 2, 1)
    );

    private final GameRepository gameRepository;
    private final PlayRepository playRepository;
    private final WatchScoreRepository watchScoreRepository;
    private final ScoreCalculator scoreCalculator;
    private final RankingService rankingService;
    private final ScoringProperties scoringProperties;

    @Scheduled(initialDelay = 10_000, fixedDelay = 10_000)
    @Transactional
    public void appendNextPlay() {
        Game game = gameRepository.findById(DevFixtureDataLoader.LIVE_GAME_ID).orElse(null);
        if (game == null || !game.isLive()) {
            return;
        }

        long nextOrder = nextPlayOrder();
        SimulatedPlay next = PLAYS.get((int) ((nextOrder - FIRST_SIMULATED_PLAY_ORDER) % PLAYS.size()));
        int homeScore = score(game.getHomeRuns()) + next.homeRuns();
        int awayScore = score(game.getAwayRuns()) + next.awayRuns();
        Play play = play(nextOrder, next, homeScore, awayScore);
        playRepository.save(play);

        game.setPeriod(next.inning());
        game.setHomeRuns(homeScore);
        game.setAwayRuns(awayScore);
        game.setUpdatedAt(Instant.now());
        gameRepository.save(game);

        List<Play> recentPlays = playRepository.findByGameIdOrderByPlayOrderDesc(
                game.getId(), PageRequest.of(0, scoringProperties.leadChange().windowPlays()));
        Collections.reverse(recentPlays);

        ScoreCalculator.Result result = scoreCalculator.calculate(game, recentPlays, Instant.now());
        double watchScore = scoreCalculator.clampWatchScore(result.baseScore());

        WatchScore record = new WatchScore();
        record.setGameId(game.getId());
        record.setBaseScore(result.baseScore());
        record.setWatchScore(watchScore);
        record.setSignals(result.signals());
        record.setReasonTags(ReasonTags.from(result.signals()));
        record.setConfigVersion(scoringProperties.version());
        record.setCreatedAt(Instant.now());
        watchScoreRepository.save(record);

        rankingService.updateLive(game.getId(), watchScore);
    }

    private long nextPlayOrder() {
        List<Play> latest = playRepository.findByGameIdOrderByPlayOrderDesc(
                DevFixtureDataLoader.LIVE_GAME_ID, PageRequest.of(0, 1));
        if (latest.isEmpty() || latest.get(0).getPlayOrder() < FIRST_SIMULATED_PLAY_ORDER) {
            return FIRST_SIMULATED_PLAY_ORDER;
        }
        return latest.get(0).getPlayOrder() + 1;
    }

    private static int score(Integer value) {
        return value == null ? 0 : value;
    }

    private static Play play(long playOrder, SimulatedPlay source, int homeScore, int awayScore) {
        Play play = new Play();
        play.setGameId(DevFixtureDataLoader.LIVE_GAME_ID);
        play.setPlayOrder(playOrder);
        play.setType("Play");
        play.setInning(source.inning());
        play.setInningType(source.inningType());
        play.setText(source.text());
        play.setHomeScore(homeScore);
        play.setAwayScore(awayScore);
        play.setScoringPlay(source.scoringPlay());
        play.setScoreValue(Boolean.TRUE.equals(source.scoringPlay()) ? source.homeRuns() + source.awayRuns() : null);
        play.setOuts(source.outs());
        play.setBalls(source.balls());
        play.setStrikes(source.strikes());
        play.setFetchedAt(Instant.now());
        return play;
    }

    private record SimulatedPlay(
            Integer inning,
            String inningType,
            String text,
            int homeRuns,
            int awayRuns,
            Boolean scoringPlay,
            Integer outs,
            Integer balls,
            Integer strikes
    ) {
    }
}
