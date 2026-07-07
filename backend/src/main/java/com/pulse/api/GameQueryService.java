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
    private static final int LLM_RECENT_PLAY_COUNT = 8;

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
            // 공개 모드는 사용자가 직접 스포일러 공개를 선택한 경우
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

                    // watch_scores 이력을 상세 화면의 누적 변동 블록으로 변환한다.
                    // 공개 모드에서도 블록 자체는 spoiler-safe 문구를 사용하고,
                    // 점수·play text 같은 공개 전용 정보는 기존 scoreSummary와 recentPlays가 담당한다.
                    liveUpdateBlocks(game.getId()),

                    DisplayMode.REVEALED
            );
        }

        // 보호 모드는 기본 응답
        // DTO 자체에서 팀명, 점수, 득점 여부, play text를 제외
        // 실수로 null이 아닌 값이 직렬화되는 위험을 줄인다.
        return new ProtectedGameDetailResponse(
                game.getId(),
                game.getStatus(),
                game.getStartTime(),

                // 보호 모드에서도 상세 페이지 상단 매치업 영역에는 팀 이름과 약어를 표시한다.
                // 점수, 승패, 팀 우세 정보는 포함하지 않으므로 스포일러 보호 정책을 유지할 수 있다.
                team(game.getHomeTeamId(), game.getHomeTeamName(), game.getHomeTeamAbbr()),
                team(game.getAwayTeamId(), game.getAwayTeamName(), game.getAwayTeamAbbr()),

                periodLabel(game),
                protectedSummary(latestScore),
                recentPlays.stream()
                        .map(GameQueryService::protectedPlayResponse)
                        .toList(),

                // protected 모드의 변동 블록은 스포일러 없는 태그와 문구만 담아야 한다.
                // watch_scores.reasonTags는 scorer가 만든 보호 모드용 추천 이유 태그이므로,
                // 상세 화면에서 누적 흐름 카드로 보여주기에 적합하다.
                liveUpdateBlocks(game.getId()),

                DisplayMode.PROTECTED
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

    private static ProtectedSummaryResponse protectedSummary(WatchScore latestScore) {
        // 아직 추천 점수가 계산되지 않은 경기일 수 있으므로 null을 허용한다.
        // protected 응답에서는 내부 점수 숫자보다 사용자가 이해할 수 있는
        // 스포일러 없는 reason tag만 내려준다.
        if (latestScore == null) {
            return new ProtectedSummaryResponse(List.of());
        }

        return new ProtectedSummaryResponse(
                latestScore.getReasonTags() == null ? List.of() : latestScore.getReasonTags()
        );
    }

    /**
     * watch_scores 전체 이력을 경기 상세 화면의 누적 변동 블록으로 변환한다.
     *
     * 이전 구현은 findTop10... 조회와 deduplicateLiveUpdateBlocks() 내부의 최대 5개 제한 때문에
     * 상세 화면에 일부 알림만 내려갔다.
     * 이제는 해당 경기의 watch_scores 전체를 최신순으로 조회한 뒤, 중복 제거 없이 모두 응답한다.
     * 프론트에서는 우측 경기 변동 알림 패널에서 스크롤로 전체 알림을 확인한다.
     */
    private List<LiveUpdateBlockResponse> liveUpdateBlocks(long gameId) {
        return watchScoreRepository.findByGameIdOrderByCreatedAtDesc(gameId).stream()
                // 최신순으로 조회한 watch_scores를 화면 표시용 블록으로 변환한다.
                .map(GameQueryService::liveUpdateBlock)
                .toList();
    }

    /**
     * WatchScore 한 건을 상세 화면용 블록 하나로 변환한다.
     *
     * 이 블록은 protected 모드에도 그대로 사용되므로 점수, 팀명, play text, 결과 문구를 넣지 않는다.
     * reasonTags만 사용해 스포일러 없는 카드 정보를 만든다.
     */
    private static LiveUpdateBlockResponse liveUpdateBlock(WatchScore watchScore) {
        List<String> reasonTags = watchScore.getReasonTags() == null
                ? List.of()
                : watchScore.getReasonTags();

        return new LiveUpdateBlockResponse(
                "최근",
                "진행 중",
                blockTitle(reasonTags),
                "긴장감 있는 흐름이 감지됐습니다.",
                reasonTags
        );
    }

    /**
     * reasonTags 중 화면 제목으로 가장 적합한 태그를 우선순위에 따라 선택한다.
     *
     * "후반 긴장 구간"처럼 넓은 구간 태그보다
     * "득점권 압박", "투수 흔들림", "장타 위험"처럼 사용자가 변화로 느끼기 쉬운 태그를 우선한다.
     */
    private static String blockTitle(List<String> reasonTags) {
        if (reasonTags == null || reasonTags.isEmpty()) {
            return "경기 흐름 변화";
        }

        List<String> priorityTags = List.of(
                "득점권 압박",
                "투수 흔들림",
                "장타 위험",
                "승부처 카운트",
                "흐름 급변",
                "한 이닝 흐름 집중",
                "접전 흐름",
                "최근 점수 변화",
                "후반 긴장 구간",
                "초반 난타 흐름"
        );

        return priorityTags.stream()
                .filter(reasonTags::contains)
                .findFirst()
                .orElse(reasonTags.get(0));
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

    /**
     * 경기 상세 화면에서 사용자가 최근 흐름 변화를 카드 형태로 볼 수 있도록 내려주는 응답.
     *
     * 이 DTO는 protected 모드에서도 사용될 수 있으므로 팀명, 점수, play text, 결과(result)처럼
     * 스포일러가 될 수 있는 값은 절대 포함하지 않음.
     */
    public record LiveUpdateBlockResponse(
            String timeLabel,           // "방금 전", "최근" 같은 표시용 시간
            String periodLabel,         // "초반", "중반", "후반", "연장"
            String title,               // "득점권 압박", "승부처 카운트" 같은 블록 제목
            String description,         // 스포일러 없는 설명 문구
            List<String> tags          // reasonTags 목록
    ) {
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

    public enum LlmPurpose {
        CARD_SUMMARY,
        NOTIFICATION,
        REPLAY_SUMMARY
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

            // 보호 모드에서도 상단 매치업 표시는 허용한다.
            // 팀 이름과 약어는 경기 식별 정보이며, 점수/승패/우세 정보는 포함하지 않는다.
            TeamResponse homeTeam,
            TeamResponse awayTeam,

            // 보호 모드에서는 정확한 점수나 팀 우세를 드러내지 않는다.
            // 이닝도 숫자 자체보다 초반/중반/후반/연장 같은 흐름 라벨로 제공한다.
            String periodLabel,

            // 보호 모드에서는 내부 watchScore 숫자를 숨긴다.
            // 사용자에게는 스포일러 없는 추천 태그만 제공한다.
            ProtectedSummaryResponse summary,

            // 보호 모드용 play 목록이다.
            // 팀명, 점수, 득점 여부, play text를 포함하지 않는다.
            List<ProtectedPlayResponse> recentPlays,

            // 경기 상세 화면에서 최근 흐름 변화를 누적 카드로 보여주기 위한 데이터다.
            // protected 모드에서도 사용되므로 점수, 팀명, play text 같은 스포일러 필드는 포함하지 않는다.
            List<LiveUpdateBlockResponse> liveUpdateBlocks,

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

            // 공개 모드에서도 상세 화면의 변동 블록을 함께 내려준다.
            // 1차 구현에서는 protected와 동일한 spoiler-safe 블록을 사용하고,
            // 점수·play text 같은 공개 전용 정보는 기존 recentPlays와 scoreSummary가 담당한다.
            List<LiveUpdateBlockResponse> liveUpdateBlocks,

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