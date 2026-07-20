package com.pulse.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * ai-service의 POST /ai/final-headline 요청 DTO입니다.
 *
 * <p>Spring Boot 내부의 {@code FinalHeadlineContext}를 ai-service HTTP 계약에 맞게 변환한 형태입니다.</p>
 *
 * <p>중요:</p>
 * <ul>
 *     <li>contextHash는 Spring Boot가 계산한 값을 그대로 전달합니다.</li>
 *     <li>PROTECTED 요청 safeContext에는 점수와 승패 관련 key 자체가 없어야 합니다.</li>
 *     <li>REVEALED 요청 safeContext에서만 finalScore, winner와 검증된 공개 근거를 포함할 수 있습니다.</li>
 * </ul>
 */
public record AiFinalHeadlineRequest(
        long gameId,
        String mode,
        String contextHash,
        SafeContext safeContext
) {

    /**
     * FINAL_HEADLINE safeContext의 공통 marker type입니다.
     *
     * <p>실제 JSON payload는 요청 모드에 따라
     * {@link ProtectedSafeContext} 또는 {@link RevealedSafeContext}로 직렬화됩니다.</p>
     */
    public sealed interface SafeContext
            permits ProtectedSafeContext, RevealedSafeContext {
    }

    /**
     * PROTECTED 모드 전용 safeContext입니다.
     *
     * <p>확정 계약상 PROTECTED 요청에는 finalScore, winner key 자체가 없어야 하므로
     * 이 record에는 해당 필드를 선언하지 않습니다.</p>
     */
    public record ProtectedSafeContext(
            String gameStatus,
            String inningPhase,
            List<String> safeTags,
            List<String> reasonCodes,
            List<KeyMoment> keyMoments
    ) implements SafeContext {

        /**
         * 리스트 필드는 null 대신 빈 리스트로 정규화합니다.
         */
        public ProtectedSafeContext {
            safeTags = safeTags == null ? List.of() : List.copyOf(safeTags);
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
            keyMoments = keyMoments == null ? List.of() : List.copyOf(keyMoments);
        }
    }

    /**
     * REVEALED 모드 전용 safeContext입니다.
     *
     * <p>공개 모드에서는 Spring Boot가 검증해 전달한 실제 경기 결과와 공개 가능한 근거만 포함합니다.
     * winner는 무승부 등 상황에서 null일 수 있으므로 class 전체 NON_NULL 전략으로 숨기지 않습니다.</p>
     */
    public record RevealedSafeContext(
            String gameStatus,
            String inningPhase,
            Teams teams,
            FinalScore finalScore,
            String winner,
            Integer inningsPlayed,
            Boolean extraInnings,
            Boolean postseason,
            List<RevealedMoment> revealedMoments,

            /*
             * FINAL_HEADLINE v2 확장 필드.
             * Spring Boot가 DB에서 조립한 검증 가능한 공개 근거만 전달합니다.
             */
            String venue,
            String startTime,
            List<Integer> homeInningScores,
            List<Integer> awayInningScores,
            SummaryFacts summaryFacts,
            List<RevealedEvent> revealedEvents,
            List<VerifiedPlay> verifiedPlays
    ) implements SafeContext {

        public RevealedSafeContext {
            revealedMoments = revealedMoments == null ? List.of() : List.copyOf(revealedMoments);
            homeInningScores = homeInningScores == null ? List.of() : List.copyOf(homeInningScores);
            awayInningScores = awayInningScores == null ? List.of() : List.copyOf(awayInningScores);
            revealedEvents = revealedEvents == null ? List.of() : List.copyOf(revealedEvents);
            verifiedPlays = verifiedPlays == null ? List.of() : List.copyOf(verifiedPlays);
        }
    }

    /**
     * 스포일러 없이 노출 가능한 핵심 순간입니다.
     *
     * <p>label은 이미 보호 표현으로 정제된 값이어야 합니다.</p>
     */
    public record KeyMoment(
            Integer inning,
            String label
    ) {
    }

    /**
     * REVEALED 모드에서만 사용할 수 있는 최종 점수 정보입니다.
     */
    public record FinalScore(
            Integer home,
            Integer away
    ) {
    }

    public record Teams(Team home, Team away) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Team(String name, String abbr) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RevealedMoment(
            Integer inning,
            String inningHalf,
            String battingTeam,
            List<String> eventTypes,
            String batter,
            Integer runsScored,
            ScoreAfter scoreAfter,
            Long scoringPlays
    ) {
        public RevealedMoment {
            eventTypes = eventTypes == null ? List.of() : List.copyOf(eventTypes);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScoreAfter(Integer home, Integer away) {
    }

    /**
     * 플레이나 이벤트에 연결된 검증된 선수 정보입니다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlayerInfo(
            Long id,
            String name
    ) {
    }

    /**
     * Spring Boot가 계산해서 전달하는 FINAL_HEADLINE v2 경기 요약 사실입니다.
     *
     * <p>LLM은 이 값을 근거로 최종 결과, 연장 여부, 총득점, 점수 차 등을 표현할 수 있습니다.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SummaryFacts(
            String winnerSide,
            String winnerName,
            String loserName,
            Integer winnerScore,
            Integer loserScore,

            String firstScoringSide,
            Integer firstScoringInning,

            Integer tyingInning,
            Integer decisiveInning,
            Integer decisiveRuns,

            Integer leadChangeCount,
            Boolean comebackWin,
            Boolean walkOff,
            Boolean shutout,
            Boolean extraInnings,
            Integer finalInning,

            Integer scoreGap,
            Integer totalRuns
    ) {
    }

    /**
     * REVEALED 모드 헤드라인에서 사용할 수 있는 공개 이벤트 근거입니다.
     *
     * <p>payload 전체가 아니라 eventType별로 허용된 evidence만 전달합니다.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RevealedEvent(
            Long eventId,
            String eventType,
            Integer inning,
            String inningType,
            PlayerInfo batter,
            PlayerInfo pitcher,
            Map<String, Object> evidence
    ) {

        public RevealedEvent {
            evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        }
    }

    /**
     * FINAL_HEADLINE 생성에 사용할 검증된 Play Result입니다.
     *
     * <p>translatedText가 있으면 ai-service prompt에서 sourceText보다 우선 사용할 수 있습니다.</p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VerifiedPlay(
            Long playId,
            Long playOrder,

            Integer inning,
            String inningType,

            String sourceText,
            String translatedText,

            Integer homeScoreAfter,
            Integer awayScoreAfter,

            Boolean scoringPlay,
            Integer scoreValue,

            Integer outs,
            Integer balls,
            Integer strikes,

            PlayerInfo batter,
            PlayerInfo pitcher,

            Boolean runnerOnFirst,
            Boolean runnerOnSecond,
            Boolean runnerOnThird,

            List<String> factTags
    ) {

        public VerifiedPlay {
            factTags = factTags == null ? List.of() : List.copyOf(factTags);
        }
    }
}
