package com.pulse.poller;

import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.Team;
import com.pulse.domain.TeamRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PollerGameWriter {

    private static final String OPERATIONAL_SOURCE = "OPERATIONAL";

    private final GameRepository gameRepository;
    private final PlayRepository playRepository;
    private final TeamRepository teamRepository;
    private final GameLifecycleStateMachine stateMachine;
    private final PollerRunnerStateMatcher runnerStateMatcher;
    private final PlayerStubWriter playerStubWriter;

    @Transactional
    public GameUpsertResult upsertGame(BdlGame dto, Instant observedAt) {
        upsertTeam(dto.homeTeam(), observedAt);
        upsertTeam(dto.awayTeam(), observedAt);

        Game game = gameRepository.findById(dto.id()).orElseGet(() -> newGame(dto.id(), observedAt));
        String previousLifecycle = game.getLifecycleState();
        boolean wasLive = GameLifecycle.LIVE.name().equals(previousLifecycle);

        game.setStatus(dto.status());
        game.setPeriod(dto.period());
        Instant startTime = parseInstant(dto.date());
        if (startTime != null) {
            game.setStartTime(startTime);
        }
        if (dto.venue() != null) {
            game.setVenue(dto.venue());
        }
        applyHomeTeam(game, dto.homeTeam(), dto.homeTeamName());
        applyAwayTeam(game, dto.awayTeam(), dto.awayTeamName());
        if (dto.homeTeamData() != null) {
            game.setHomeRuns(dto.homeTeamData().runs());
            game.setHomeInningScores(dto.homeTeamData().inningScores());
        }
        if (dto.awayTeamData() != null) {
            game.setAwayRuns(dto.awayTeamData().runs());
            game.setAwayInningScores(dto.awayTeamData().inningScores());
        }

        GameLifecycle nextLifecycle = stateMachine.transition(
                previousLifecycle,
                dto.status(),
                game.getStartTime(),
                observedAt
        );
        game.setLifecycleState(nextLifecycle.name());
        game.setLastPolledAt(observedAt);
        game.setObservedAt(observedAt);
        game.setUpdatedAt(observedAt);
        game.setSource(OPERATIONAL_SOURCE);

        Game saved = gameRepository.save(game);
        return new GameUpsertResult(saved, previousLifecycle, saved.getLifecycleState(), wasLive);
    }

    @Transactional
    public boolean appendPlay(Game game, BdlPlay dto, Instant observedAt) {
        if (dto.order() == null || playRepository.existsByGameIdAndPlayOrder(game.getId(), dto.order())) {
            return false;
        }
        playerStubWriter.ensurePlayerExists(dto.batterId(), observedAt);
        playerStubWriter.ensurePlayerExists(dto.pitcherId(), observedAt);
        Play play = new Play();
        play.setGameId(game.getId());
        play.setPlayOrder(dto.order());
        play.setType(dto.type());
        play.setInning(dto.inning());
        play.setInningType(dto.inningType());
        play.setText(dto.text());
        play.setHomeScore(dto.homeScore());
        play.setAwayScore(dto.awayScore());
        play.setScoringPlay(dto.scoringPlay());
        play.setScoreValue(dto.scoreValue());
        play.setOuts(dto.outs());
        play.setBalls(dto.balls());
        play.setStrikes(dto.strikes());
        play.setBatterId(dto.batterId());
        play.setPitcherId(dto.pitcherId());
        play.setFetchedAt(observedAt);
        play.setBackfilled(false);
        play.setSource(OPERATIONAL_SOURCE);
        playRepository.save(play);

        game.setLastPlayOrder(dto.order());
        game.setLastPolledAt(observedAt);
        game.setObservedAt(observedAt);
        game.setUpdatedAt(observedAt);
        gameRepository.save(game);
        return true;
    }

    @Transactional
    public PollerRunnerStateMatcher.MatchResult updateRunnerStates(
            long gameId,
            List<BdlPlateAppearance> plateAppearances
    ) {
        List<Play> plays = playRepository.findByGameIdOrderByPlayOrderAsc(gameId);
        PollerRunnerStateMatcher.MatchResult result = runnerStateMatcher.match(plays, plateAppearances);
        Map<Long, PollerRunnerStateMatcher.RunnerStateUpdate> updatesByOrder = result.updates().stream()
                .collect(Collectors.toMap(
                        PollerRunnerStateMatcher.RunnerStateUpdate::playOrder,
                        Function.identity(),
                        (left, right) -> right
                ));
        List<Play> changedPlays = plays.stream().filter(play -> {
            PollerRunnerStateMatcher.RunnerStateUpdate update = updatesByOrder.get(play.getPlayOrder());
            if (update == null) {
                return false;
            }
            return !Objects.equals(play.getRunnerOnFirst(), update.runnerOnFirst())
                    || !Objects.equals(play.getRunnerOnSecond(), update.runnerOnSecond())
                    || !Objects.equals(play.getRunnerOnThird(), update.runnerOnThird());
        }).toList();
        for (Play play : changedPlays) {
            PollerRunnerStateMatcher.RunnerStateUpdate update = updatesByOrder.get(play.getPlayOrder());
            play.setRunnerOnFirst(update.runnerOnFirst());
            play.setRunnerOnSecond(update.runnerOnSecond());
            play.setRunnerOnThird(update.runnerOnThird());
        }
        playRepository.saveAll(changedPlays);
        return result;
    }

    private Game newGame(long gameId, Instant now) {
        Game game = new Game();
        game.setId(gameId);
        game.setCreatedAt(now);
        return game;
    }

    private void upsertTeam(BdlGame.Team dto, Instant observedAt) {
        if (dto == null) {
            return;
        }
        Team team = teamRepository.findById(dto.id()).orElseGet(() -> {
            Team created = new Team();
            created.setTeamId(dto.id());
            created.setCreatedAt(observedAt);
            return created;
        });
        team.setDisplayName(dto.displayName());
        team.setAbbreviation(dto.abbreviation());
        team.setUpdatedAt(observedAt);
        teamRepository.save(team);
    }

    private static void applyHomeTeam(Game game, BdlGame.Team team, String fallbackName) {
        if (team == null && !hasText(fallbackName)) {
            return;
        }
        if (team != null) {
            game.setHomeTeamId(team.id());
        }
        game.setHomeTeamName(resolveTeamName(team, fallbackName));
        game.setHomeTeamAbbr(resolveTeamAbbreviation(team));
    }

    private static void applyAwayTeam(Game game, BdlGame.Team team, String fallbackName) {
        if (team == null && !hasText(fallbackName)) {
            return;
        }
        if (team != null) {
            game.setAwayTeamId(team.id());
        }
        game.setAwayTeamName(resolveTeamName(team, fallbackName));
        game.setAwayTeamAbbr(resolveTeamAbbreviation(team));
    }

    private static String resolveTeamName(BdlGame.Team team, String fallbackName) {
        if ((team == null || isUnknownTeam(team)) && hasText(fallbackName)) {
            return fallbackName;
        }
        if (team != null && hasText(team.displayName())) {
            return team.displayName();
        }
        return hasText(fallbackName) ? fallbackName : null;
    }

    private static String resolveTeamAbbreviation(BdlGame.Team team) {
        return team == null || isUnknownTeam(team) ? null : team.abbreviation();
    }

    private static boolean isUnknownTeam(BdlGame.Team team) {
        return team.id() < 0
                || "UNK".equalsIgnoreCase(team.abbreviation())
                || "Unknown".equalsIgnoreCase(team.displayName());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public record GameUpsertResult(
            Game game,
            String previousLifecycle,
            String currentLifecycle,
            boolean wasLive
    ) {

        public boolean enteredLive() {
            return !Objects.equals(previousLifecycle, currentLifecycle)
                    && GameLifecycle.LIVE.name().equals(currentLifecycle);
        }

        public boolean enteredTerminalState() {
            return !Objects.equals(previousLifecycle, currentLifecycle)
                    && (GameLifecycle.FINAL.name().equals(currentLifecycle)
                    || GameLifecycle.DONE.name().equals(currentLifecycle)
                    || GameLifecycle.SUSPENDED_POSTPONED.name().equals(currentLifecycle));
        }
    }
}
