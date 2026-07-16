package com.pulse.common.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 종료 경기 FINAL_HEADLINE 생성에 사용하는 내부 context입니다.
 *
 * <p>PROTECTED 모드는 기존 보호형 필드만 사용합니다.</p>
 *
 * <p>REVEALED 모드는 실제 팀 정보, 이닝별 득점, 공개 이벤트,
 * 검증된 플레이 및 서버가 계산한 경기 요약 사실을 추가로 사용할 수 있습니다.</p>
 *
 * <p>이 record는 Spring Boot 내부 계약입니다.
 * 실제 ai-service HTTP 요청 형태는 AiFinalHeadlineRequest에서 다시 정의합니다.</p>
 */
public record FinalHeadlineContext(
        long gameId,
        AiCopyMode mode,
        String status,
        String periodLabel,
        List<String> reasonTags,
        List<String> spoilerSafeSignals,
        List<KeyMoment> keyMoments,

        /*
         * 기존 main에서 이미 사용 중인 필드입니다.
         * AiCopyContextService 호환성을 위해 유지합니다.
         */
        Teams teams,
        FinalScore finalScore,
        String winner,
        Integer inningsPlayed,
        Boolean extraInnings,
        Boolean postseason,
        List<RevealedMoment> revealedMoments,

        /*
         * FINAL_HEADLINE v2 확장 필드입니다.
         * 다음 단계에서 AiCopyContextService가 실제 DB 값으로 채웁니다.
         */
        String venue,
        Instant startTime,
        List<Integer> homeInningScores,
        List<Integer> awayInningScores,
        SummaryFacts summaryFacts,
        List<RevealedEvent> revealedEvents,
        List<VerifiedPlay> verifiedPlays,

        String contextHash
) {

    /**
     * 리스트 필드를 null 대신 불변 빈 목록으로 정규화합니다.
     *
     * PROTECTED 모드에서는 REVEALED 전용 리스트가 비어 있을 수 있습니다.
     */
    public FinalHeadlineContext {
        reasonTags = immutableList(reasonTags);
        spoilerSafeSignals = immutableList(spoilerSafeSignals);
        keyMoments = immutableList(keyMoments);

        revealedMoments = immutableList(revealedMoments);

        homeInningScores = immutableList(homeInningScores);
        awayInningScores = immutableList(awayInningScores);

        revealedEvents = immutableList(revealedEvents);
        verifiedPlays = immutableList(verifiedPlays);
    }

    /**
     * 기존 FINAL_HEADLINE 보호 컨텍스트 생성 코드와 테스트의 호환성을 유지하는 생성자입니다.
     */
    public FinalHeadlineContext(
            long gameId,
            AiCopyMode mode,
            String status,
            String periodLabel,
            List<String> reasonTags,
            List<String> spoilerSafeSignals,
            List<KeyMoment> keyMoments,
            FinalScore finalScore,
            String winner,
            String contextHash
    ) {
        this(
                gameId,
                mode,
                status,
                periodLabel,
                reasonTags,
                spoilerSafeSignals,
                keyMoments,

                null,
                finalScore,
                winner,
                null,
                null,
                null,
                List.of(),

                null,
                null,
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),

                contextHash
        );
    }

    /**
     * 현재 main의 AiCopyContextService가 사용하는 생성자와 호환성을 유지합니다.
     */
    public FinalHeadlineContext(
            long gameId,
            AiCopyMode mode,
            String status,
            String periodLabel,
            List<String> reasonTags,
            List<String> spoilerSafeSignals,
            List<KeyMoment> keyMoments,
            Teams teams,
            FinalScore finalScore,
            String winner,
            Integer inningsPlayed,
            Boolean extraInnings,
            Boolean postseason,
            List<RevealedMoment> revealedMoments,
            String contextHash
    ) {
        this(
                gameId,
                mode,
                status,
                periodLabel,
                reasonTags,
                spoilerSafeSignals,
                keyMoments,

                teams,
                finalScore,
                winner,
                inningsPlayed,
                extraInnings,
                postseason,
                revealedMoments,

                null,
                null,
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),

                contextHash
        );
    }

    /**
     * 스포일러 없이 노출 가능한 기존 핵심 순간입니다.
     */
    public record KeyMoment(
            Integer inning,
            String label
    ) {
    }

    /**
     * 기존 main에서 사용하는 팀 묶음 타입입니다.
     */
    public record Teams(
            Team home,
            Team away
    ) {
    }

    /**
     * 기존 main에서 사용하는 팀 타입입니다.
     */
    public record Team(
            String name,
            String abbr
    ) {
    }

    /**
     * 홈팀-원정팀 순서의 실제 최종 점수입니다.
     */
    public record FinalScore(
            Integer home,
            Integer away
    ) {
    }

    /**
     * 기존 main에서 사용하는 공개 순간 타입입니다.
     */
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
            eventTypes = immutableList(eventTypes);
        }
    }

    /**
     * 기존 main에서 사용하는 공개 순간 이후 점수 타입입니다.
     */
    public record ScoreAfter(
            Integer home,
            Integer away
    ) {
    }

    /**
     * FINAL_HEADLINE v2 경기 팀 정보입니다.
     *
     * id:
     * - DB에 저장된 팀 ID
     *
     * name:
     * - 정식 팀명
     *
     * abbr:
     * - 화면과 짧은 헤드라인에서 사용할 수 있는 팀 약어
     */
    public record TeamInfo(
            Long id,
            String name,
            String abbr
    ) {
    }

    /**
     * 플레이나 이벤트에 연결된 검증된 선수 정보입니다.
     */
    public record PlayerInfo(
            Long id,
            String name
    ) {
    }

    /**
     * Spring Boot가 점수 진행과 경기 데이터를 바탕으로 계산한
     * FINAL_HEADLINE 전용 경기 전체 요약 사실입니다.
     *
     * LLM은 이 값을 근거로 역전·끝내기·영봉·연장 등의 표현을 사용합니다.
     */
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
     * REVEALED 모드 헤드라인에서 사용할 수 있는 공개 이벤트입니다.
     *
     * evidence에는 eventType별로 허용된 값만 담습니다.
     * 원본 payload 전체나 내부 추천 점수는 포함하지 않습니다.
     */
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
            evidence = immutableMap(evidence);
        }
    }

    /**
     * FINAL_HEADLINE 생성에 사용할 검증된 Play Result입니다.
     *
     * sourceText:
     * - 원본 MLB Play Result
     *
     * translatedText:
     * - PLAY_TRANSLATION 검수와 저장을 통과한 한국어 번역
     *
     * factTags:
     * - SCORING_PLAY, LEAD_CHANGE, DECISIVE_SCORE 등의 서버 계산 태그
     */
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
            factTags = immutableList(factTags);
        }
    }

    /**
     * null 또는 빈 리스트를 불변 빈 목록으로 바꿉니다.
     *
     * ArrayList 복사를 사용하므로 원본 리스트가 이후 변경되더라도
     * FINAL_HEADLINE context에는 영향을 주지 않습니다.
     */
    private static <T> List<T> immutableList(
            List<T> values
    ) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return Collections.unmodifiableList(
                new ArrayList<>(values)
        );
    }

    /**
     * 이벤트 evidence를 불변 map으로 복사합니다.
     */
    private static <K, V> Map<K, V> immutableMap(
            Map<K, V> values
    ) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        return Collections.unmodifiableMap(
                new LinkedHashMap<>(values)
        );
    }
}
