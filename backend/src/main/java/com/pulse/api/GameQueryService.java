package com.pulse.api;

import com.pulse.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GameQueryService {

    private static final int DETAIL_RECENT_PLAY_COUNT = 20;

    private final GameRepository gameRepository;
    private final PlayRepository playRepository;
    private final WatchScoreRepository watchScoreRepository;

    public GameDetailView getGameDetail(long gameId, String mode) {
        Game game = findGame(gameId);
        WatchScore latestScore = latestScore(gameId);
        List<Play> recentPlays = recentPlays(gameId, DETAIL_RECENT_PLAY_COUNT);

        // mode 파라미터는 사용자가 직접 입력하는 값이므로 대소문자 차이와 공백을 방어한다.
        // 잘못된 값이 들어오면 스포일러 보호 원칙에 따라 PROTECTED로 처리한다.
        DisplayMode safeMode = parseDisplayMode(mode);

        // 기존 코드에서 NORMAL을 쓰고 있을 수 있으므로
        // 당장은 NORMAL도 REVEALED와 같은 공개 모드로 취급한다.
        // 추후 NORMAL은 제거 가능
        boolean revealed = safeMode == DisplayMode.REVEALED || safeMode == DisplayMode.NORMAL;

        if (revealed) {
            // 공개 모드는 사용자가 직접 스포일러 공개를 선택한 경우다.
            // 따라서 팀명, 점수, play text, 득점 관련 필드를 포함한다.
            return new RevealedGameDetailResponse(
                    game.getId(),
                    game.getStatus(),
                    game.getStartTime(),
                    game.getPeriod(),
                    team(game.getHomeTeamId(), game.getHomeTeamName(), game.getHomeTeamAbbr()),
                    team(game.getAwayTeamId(), game.getAwayTeamName(), game.getAwayTeamAbbr()),
                    score(game),
                    scoreSummary(latestScore),
                    recentPlays.stream()
                            .map(GameQueryService::revealedPlayResponse)
                            .toList(),
                    DisplayMode.REVEALED
            );
        }

        // 보호 모드는 기본 응답이다.
        // DTO 자체에서 팀명, 점수, 득점 여부, play text를 제외해
        // 실수로 null이 아닌 값이 직렬화되는 위험을 줄인다.
        return new ProtectedGameDetailResponse(
                game.getId(),
                game.getStatus(),
                game.getStartTime(),
                periodLabel(game),
                protectedSummary(latestScore),
                recentPlays.stream()
                        .map(GameQueryService::protectedPlayResponse)
                        .toList(),
                DisplayMode.PROTECTED
        );
    }


    private Game findGame(long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "game not found: " + gameId));
    }

    private WatchScore latestScore(long gameId) {
        return watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(gameId).orElse(null);
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
                numericScore(latestScore.getBaseScore()),
                numericScore(latestScore.getWatchScore()),
                latestScore.getSignalContributions(),
                latestScore.getTags(),
                null,
                latestScore.getComputedAt()
        );
    }

    private static ProtectedSummaryResponse protectedSummary(WatchScore latestScore) {
        // 아직 추천 점수가 계산되지 않은 경기일 수 있으므로 null을 허용한다.
        // protected 응답에서는 내부 점수 숫자보다 사용자가 이해할 수 있는
        // 스포일러 없는 reason tag만 내려준다.
        if (latestScore == null) {
            return new ProtectedSummaryResponse(List.of());
        }

        return new ProtectedSummaryResponse(
                latestScore.getTags() == null ? List.of() : latestScore.getTags()
        );
    }

    private static double numericScore(Integer score) {
        return score == null ? 0.0 : score.doubleValue();
    }




    private static RevealedPlayResponse revealedPlayResponse(Play play) {
        // 공개 모드에서는 사용자가 스포일러 공개를 선택했으므로
        // play text, 점수 변화, 득점 여부를 모두 내려줄 수 있다.
        return new RevealedPlayResponse(
                play.getId(),
                play.getPlayOrder(),
                play.getType(),
                play.getInning(),
                play.getInningType(),
                play.getText(),
                play.getHomeScore(),
                play.getAwayScore(),
                play.getScoringPlay(),
                play.getScoreValue(),
                play.getOuts(),
                play.getBalls(),
                play.getStrikes(),
                play.getFetchedAt()
        );
    }

    private static ProtectedPlayResponse protectedPlayResponse(Play play) {
        // 보호 모드에서는 play text를 제외한다.
        // text에는 "homered", "scored", "RBI", "walk-off"처럼
        // 결과를 직접 드러내는 문구가 포함될 수 있기 때문이다.
        //
        // homeScore, awayScore, scoringPlay, scoreValue도 제외한다.
        // 이 값들은 점수 변화와 득점 발생 여부를 직접 드러낸다.
        return new ProtectedPlayResponse(
                play.getType(),
                play.getInning(),
                play.getInningType(),
                play.getOuts(),
                play.getBalls(),
                play.getStrikes()
        );
    }


    private static DisplayMode parseDisplayMode(String mode) {
        // mode가 없거나 공백이면 기본값은 PROTECTED다.
        // PULSE는 사용자가 명시적으로 공개를 선택하기 전까지 스포일러를 숨긴다.
        if (mode == null || mode.isBlank()) {
            return DisplayMode.PROTECTED;
        }

        String normalizedMode = mode.trim().toUpperCase();

        // 프론트/사용자 요청은 보통 protected/revealed 소문자로 들어올 수 있다.
        // 내부 enum은 대문자이므로 여기서 한 번 정규화한다.
        try {
            return DisplayMode.valueOf(normalizedMode);
        } catch (IllegalArgumentException e) {
            // 알 수 없는 mode 값은 에러로 공개하지 않고 보호 모드로 처리한다.
            // 잘못된 요청 때문에 스포일러가 노출되는 일을 막기 위한 안전장치다.
            return DisplayMode.PROTECTED;
        }
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
        // 기본값. 점수, 팀명, 승패, 결과성 play 정보를 숨긴다.
        PROTECTED,

        // 사용자가 직접 스포일러 공개를 선택한 경우에만 사용한다.
        REVEALED,

        // 기존 코드 호환용이다.
        // 새 API 문서와 프론트에서는 REVEALED 사용을 권장한다.
        NORMAL
    }


    public sealed interface GameDetailView
            permits ProtectedGameDetailResponse, RevealedGameDetailResponse {
        // 경기 상세 응답의 공통 타입이다.
        // protected와 revealed는 응답 필드가 다르지만,
        // 컨트롤러에서는 "경기 상세 응답"이라는 하나의 타입으로 반환하기 위해 사용한다.
    }

    public record ProtectedGameDetailResponse(
            long gameId,
            String status,
            Instant startTime,

            // 보호 모드에서는 정확한 점수나 팀 우세를 드러내지 않는다.
            // 이닝도 숫자 자체보다 초반/중반/후반/연장 같은 흐름 라벨로 제공한다.
            String periodLabel,

            // 보호 모드에서는 내부 watchScore 숫자를 숨긴다.
            // 사용자에게는 스포일러 없는 추천 태그만 제공한다.
            ProtectedSummaryResponse summary,

            // 보호 모드용 play 목록이다.
            // 팀명, 점수, 득점 여부, play text를 포함하지 않는다.
            List<ProtectedPlayResponse> recentPlays,

            DisplayMode displayMode
    ) implements GameDetailView {
    }


    public record RevealedGameDetailResponse(
            long gameId,
            String status,
            Instant startTime,
            Integer period,

            // 공개 모드에서는 사용자가 스포일러 공개를 선택했으므로
            // 홈/원정 팀 정보를 제공한다.
            TeamResponse homeTeam,
            TeamResponse awayTeam,

            // 공개 모드에서만 점수를 제공한다.
            ScoreResponse score,

            // 공개 모드에서는 내부 추천 점수와 signal도 확인할 수 있다.
            ScoreSummaryResponse scoreSummary,

            // 공개 모드용 play 목록이다.
            // play text, 점수 변화, 득점 여부를 포함한다.
            List<RevealedPlayResponse> recentPlays,

            DisplayMode displayMode
    ) implements GameDetailView {
    }



    public record ProtectedSummaryResponse(
            // 예: 후반 긴장 구간, 득점권 압박, 접전 흐름
            // 단, 홈런 발생/역전 성공/끝내기 같은 결과성 태그는 넣으면 안 된다.
            List<String> reasonTags
    ) {
    }


    public record RevealedPlayResponse(
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


    public record ProtectedPlayResponse(
            String type,
            Integer inning,
            String inningType,
            Integer outs,
            Integer balls,
            Integer strikes
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
            Integer configVersion,
            Instant calculatedAt
    ) {
    }


}
