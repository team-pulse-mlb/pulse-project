package com.pulse.api;

import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GameQueryService {

    private static final int DETAIL_RECENT_PLAY_COUNT = 20;
    private static final int LLM_RECENT_PLAY_COUNT = 8;

    private final GameRepository gameRepository;
    private final PlayRepository playRepository;
    private final WatchScoreRepository watchScoreRepository;

    public GameDetailResponse getGameDetail(long gameId, DisplayMode displayMode) {
        Game game = findGame(gameId);
        WatchScore latestScore = latestScore(gameId);
        List<Play> recentPlays = recentPlays(gameId, DETAIL_RECENT_PLAY_COUNT);

        return new GameDetailResponse(
                game.getId(),
                game.getStatus(),
                game.getStartTime(),
                game.getPeriod(),
                team(game.getHomeTeamId(), game.getHomeTeamName(), game.getHomeTeamAbbr()),
                team(game.getAwayTeamId(), game.getAwayTeamName(), game.getAwayTeamAbbr()),
                displayMode == DisplayMode.NORMAL ? score(game) : null,
                scoreSummary(latestScore),
                recentPlays.stream()
                        .map(play -> playResponse(play, displayMode))
                        .toList(),
                displayMode
        );
    }

    public SpoilerFreeLlmContextResponse getSpoilerFreeLlmContext(long gameId, LlmPurpose purpose) {
        Game game = findGame(gameId);
        WatchScore latestScore = latestScore(gameId);
        List<Play> recentPlays = recentPlays(gameId, LLM_RECENT_PLAY_COUNT);
        Map<String, Double> signals = latestScore == null ? Map.of() : latestScore.getSignals();

        return new SpoilerFreeLlmContextResponse(
                game.getId(),
                purpose,
                game.getStatus(),
                game.getStartTime(),
                periodLabel(game),
                List.of(
                        team(game.getHomeTeamId(), game.getHomeTeamName(), game.getHomeTeamAbbr()),
                        team(game.getAwayTeamId(), game.getAwayTeamName(), game.getAwayTeamAbbr())
                ),
                latestScore == null ? List.of() : latestScore.getReasonTags(),
                positiveSignalKeys(signals),
                recentPlays.stream()
                        .map(GameQueryService::spoilerSafePlay)
                        .toList()
        );
    }

    private Game findGame(long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "game not found: " + gameId));
    }

    private WatchScore latestScore(long gameId) {
        return watchScoreRepository.findTopByGameIdOrderByCreatedAtDesc(gameId).orElse(null);
    }

    private List<Play> recentPlays(long gameId, int count) {
        List<Play> plays = playRepository.findByGameIdOrderByPlayOrderDesc(gameId, PageRequest.of(0, count));
        Collections.reverse(plays);
        return plays;
    }

    private static TeamResponse team(Long id, String name, String abbr) {
        return new TeamResponse(id, name, abbr);
    }

    private static ScoreResponse score(Game game) {
        return new ScoreResponse(game.getHomeRuns(), game.getAwayRuns());
    }

    private static ScoreSummaryResponse scoreSummary(WatchScore latestScore) {
        if (latestScore == null) {
            return null;
        }
        return new ScoreSummaryResponse(
                latestScore.getBaseScore(),
                latestScore.getWatchScore(),
                latestScore.getSignals(),
                latestScore.getReasonTags(),
                latestScore.getConfigVersion(),
                latestScore.getCreatedAt()
        );
    }

    private static PlayResponse playResponse(Play play, DisplayMode displayMode) {
        boolean normal = displayMode == DisplayMode.NORMAL;
        return new PlayResponse(
                play.getId(),
                play.getPlayOrder(),
                play.getType(),
                play.getInning(),
                play.getInningType(),
                play.getText(),
                normal ? play.getHomeScore() : null,
                normal ? play.getAwayScore() : null,
                normal ? play.getScoringPlay() : null,
                normal ? play.getScoreValue() : null,
                play.getOuts(),
                play.getBalls(),
                play.getStrikes(),
                play.getFetchedAt()
        );
    }

    private static SpoilerSafePlayResponse spoilerSafePlay(Play play) {
        return new SpoilerSafePlayResponse(
                play.getType(),
                play.getInning(),
                play.getInningType(),
                play.getText(),
                play.getOuts(),
                play.getBalls(),
                play.getStrikes()
        );
    }

    private static List<String> positiveSignalKeys(Map<String, Double> signals) {
        if (signals == null) {
            return List.of();
        }
        return signals.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static String periodLabel(Game game) {
        if (game.isFinal()) {
            return "경기 종료";
        }
        if (!game.isLive()) {
            return "경기 전";
        }
        Integer period = game.getPeriod();
        if (period == null) {
            return "진행 중";
        }
        if (period >= 10) {
            return "연장";
        }
        if (period >= 7) {
            return "후반";
        }
        if (period >= 4) {
            return "중반";
        }
        return "초반";
    }

    public enum DisplayMode {
        NORMAL,
        PROTECTED
    }

    public enum LlmPurpose {
        CARD_SUMMARY,
        NOTIFICATION,
        REPLAY_SUMMARY
    }

    public record GameDetailResponse(
            long gameId,
            String status,
            Instant startTime,
            Integer period,
            TeamResponse homeTeam,
            TeamResponse awayTeam,
            ScoreResponse score,
            ScoreSummaryResponse scoreSummary,
            List<PlayResponse> recentPlays,
            DisplayMode displayMode
    ) {
    }

    public record SpoilerFreeLlmContextResponse(
            long gameId,
            LlmPurpose purpose,
            String status,
            Instant startTime,
            String periodLabel,
            List<TeamResponse> teams,
            List<String> reasonTags,
            List<String> spoilerSafeSignals,
            List<SpoilerSafePlayResponse> recentPlays
    ) {
    }

    public record TeamResponse(Long id, String name, String abbr) {
    }

    public record ScoreResponse(Integer home, Integer away) {
    }

    public record ScoreSummaryResponse(
            double baseScore,
            double watchScore,
            Map<String, Double> signals,
            List<String> reasonTags,
            int configVersion,
            Instant calculatedAt
    ) {
    }

    public record PlayResponse(
            Long id,
            Long playOrder,
            String type,
            Integer inning,
            String inningType,
            String text,
            Integer homeScore,
            Integer awayScore,
            Boolean scoringPlay,
            Integer scoreValue,
            Integer outs,
            Integer balls,
            Integer strikes,
            Instant fetchedAt
    ) {
    }

    public record SpoilerSafePlayResponse(
            String type,
            Integer inning,
            String inningType,
            String text,
            Integer outs,
            Integer balls,
            Integer strikes
    ) {
    }
}
