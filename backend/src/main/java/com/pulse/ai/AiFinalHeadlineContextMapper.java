package com.pulse.ai;

import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.FinalHeadlineContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * {@link FinalHeadlineContext}를 ai-service /ai/final-headline 요청 DTO로 변환합니다.
 *
 * <p>중요한 책임 경계:</p>
 * <ul>
 *     <li>{@code FinalHeadlineContext}는 {@code AiCopyContextReader}가 반환한 검증 완료 context입니다.</li>
 *     <li>이 mapper는 safeContext를 새로 판단하거나 contextHash를 재계산하지 않습니다.</li>
 *     <li>필드명을 ai-service HTTP 계약에 맞게 옮기는 역할만 합니다.</li>
 *     <li>PROTECTED와 REVEALED의 JSON key 차이는 DTO 타입 분리로 보장합니다.</li>
 * </ul>
 */
@Component
public class AiFinalHeadlineContextMapper {

    /**
     * 검증 완료된 FINAL_HEADLINE context를 ai-service 요청 객체로 변환합니다.
     *
     * @param context Spring Boot가 생성한 FINAL_HEADLINE safeContext
     * @return ai-service POST /ai/final-headline 요청 DTO
     */
    public AiFinalHeadlineRequest toRequest(
            FinalHeadlineContext context
    ) {
        return new AiFinalHeadlineRequest(
                context.gameId(),
                context.mode().name(),
                context.contextHash(),
                toSafeContext(context)
        );
    }

    /**
     * FinalHeadlineContext의 safe field를 ai-service safeContext 계약에 맞게 옮깁니다.
     *
     * <p>매핑 기준:</p>
     * <ul>
     *     <li>status → gameStatus</li>
     *     <li>periodLabel → inningPhase</li>
     *     <li>reasonTags → safeTags</li>
     *     <li>spoilerSafeSignals → reasonCodes</li>
     *     <li>keyMoments → keyMoments</li>
     *     <li>teams·finalScore·winner·inningsPlayed·extraInnings·postseason·revealedMoments → REVEALED 전용</li>
     *     <li>venue·startTime·inningScores·summaryFacts·revealedEvents·verifiedPlays → REVEALED v2 전용</li>
     * </ul>
     */
    private AiFinalHeadlineRequest.SafeContext toSafeContext(
            FinalHeadlineContext context
    ) {
        if (context.mode() == AiCopyMode.REVEALED) {
            return new AiFinalHeadlineRequest.RevealedSafeContext(
                    context.status(),
                    context.periodLabel(),
                    toTeams(context.teams()),
                    toFinalScore(context.finalScore()),
                    context.winner(),
                    context.inningsPlayed(),
                    context.extraInnings(),
                    context.postseason(),
                    toRevealedMoments(context.revealedMoments()),

                    context.venue(),
                    toIsoString(context.startTime()),
                    copyList(context.homeInningScores()),
                    copyList(context.awayInningScores()),
                    toSummaryFacts(context.summaryFacts()),
                    toRevealedEvents(context.revealedEvents()),
                    toVerifiedPlays(context.verifiedPlays())
            );
        }

        return new AiFinalHeadlineRequest.ProtectedSafeContext(
                context.status(),
                context.periodLabel(),
                // PROTECTED에서도 safeTags/reasonCodes/keyMoments만 전달합니다.
                copyList(context.reasonTags()),
                copyList(context.spoilerSafeSignals()),
                toKeyMoments(context.keyMoments())
        );
    }

    /**
     * keyMoments는 이미 보호 표현으로 정제된 값이므로, 값의 의미를 바꾸지 않고 DTO 타입만 변환합니다.
     */
    private List<AiFinalHeadlineRequest.KeyMoment> toKeyMoments(
            List<FinalHeadlineContext.KeyMoment> keyMoments
    ) {
        if (keyMoments == null || keyMoments.isEmpty()) {
            return List.of();
        }

        return keyMoments.stream()
                .map(keyMoment -> new AiFinalHeadlineRequest.KeyMoment(
                        keyMoment.inning(),
                        keyMoment.label()
                ))
                .toList();
    }

    /**
     * REVEALED 모드에서만 finalScore가 들어올 수 있습니다.
     * PROTECTED 모드에서는 이 메서드를 호출하지 않는 구조로 key 자체 노출을 방지합니다.
     */
    private AiFinalHeadlineRequest.FinalScore toFinalScore(
            FinalHeadlineContext.FinalScore finalScore
    ) {
        if (finalScore == null) {
            return null;
        }

        return new AiFinalHeadlineRequest.FinalScore(
                finalScore.home(),
                finalScore.away()
        );
    }

    private AiFinalHeadlineRequest.Teams toTeams(FinalHeadlineContext.Teams teams) {
        if (teams == null) {
            return null;
        }
        return new AiFinalHeadlineRequest.Teams(toTeam(teams.home()), toTeam(teams.away()));
    }

    private AiFinalHeadlineRequest.Team toTeam(FinalHeadlineContext.Team team) {
        return team == null ? null : new AiFinalHeadlineRequest.Team(team.name(), team.abbr());
    }

    private List<AiFinalHeadlineRequest.RevealedMoment> toRevealedMoments(
            List<FinalHeadlineContext.RevealedMoment> moments
    ) {
        if (moments == null || moments.isEmpty()) {
            return List.of();
        }
        return moments.stream()
                .map(moment -> new AiFinalHeadlineRequest.RevealedMoment(
                        moment.inning(),
                        moment.inningHalf(),
                        moment.battingTeam(),
                        copyList(moment.eventTypes()),
                        moment.batter(),
                        moment.runsScored(),
                        toScoreAfter(moment.scoreAfter()),
                        moment.scoringPlays()))
                .toList();
    }

    private AiFinalHeadlineRequest.ScoreAfter toScoreAfter(FinalHeadlineContext.ScoreAfter scoreAfter) {
        return scoreAfter == null
                ? null
                : new AiFinalHeadlineRequest.ScoreAfter(scoreAfter.home(), scoreAfter.away());
    }

    /**
     * FINAL_HEADLINE v2 summaryFacts를 ai-service HTTP DTO로 변환합니다.
     */
    private AiFinalHeadlineRequest.SummaryFacts toSummaryFacts(
            FinalHeadlineContext.SummaryFacts summaryFacts
    ) {
        if (summaryFacts == null) {
            return null;
        }

        return new AiFinalHeadlineRequest.SummaryFacts(
                summaryFacts.winnerSide(),
                summaryFacts.winnerName(),
                summaryFacts.loserName(),
                summaryFacts.winnerScore(),
                summaryFacts.loserScore(),

                summaryFacts.firstScoringSide(),
                summaryFacts.firstScoringInning(),

                summaryFacts.tyingInning(),
                summaryFacts.decisiveInning(),
                summaryFacts.decisiveRuns(),

                summaryFacts.leadChangeCount(),
                summaryFacts.comebackWin(),
                summaryFacts.walkOff(),
                summaryFacts.shutout(),
                summaryFacts.extraInnings(),
                summaryFacts.finalInning(),

                summaryFacts.scoreGap(),
                summaryFacts.totalRuns()
        );
    }

    /**
     * 공개 이벤트 근거를 ai-service HTTP DTO로 변환합니다.
     */
    private List<AiFinalHeadlineRequest.RevealedEvent> toRevealedEvents(
            List<FinalHeadlineContext.RevealedEvent> events
    ) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        return events.stream()
                .map(event -> new AiFinalHeadlineRequest.RevealedEvent(
                        event.eventId(),
                        event.eventType(),
                        event.inning(),
                        event.inningType(),
                        toPlayerInfo(event.batter()),
                        toPlayerInfo(event.pitcher()),
                        event.evidence()
                ))
                .toList();
    }

    /**
     * FINAL_HEADLINE v2 검증 플레이 근거를 ai-service HTTP DTO로 변환합니다.
     */
    private List<AiFinalHeadlineRequest.VerifiedPlay> toVerifiedPlays(
            List<FinalHeadlineContext.VerifiedPlay> plays
    ) {
        if (plays == null || plays.isEmpty()) {
            return List.of();
        }

        return plays.stream()
                .map(play -> new AiFinalHeadlineRequest.VerifiedPlay(
                        play.playId(),
                        play.playOrder(),

                        play.inning(),
                        play.inningType(),

                        play.sourceText(),
                        play.translatedText(),

                        play.homeScoreAfter(),
                        play.awayScoreAfter(),

                        play.scoringPlay(),
                        play.scoreValue(),

                        play.outs(),
                        play.balls(),
                        play.strikes(),

                        toPlayerInfo(play.batter()),
                        toPlayerInfo(play.pitcher()),

                        play.runnerOnFirst(),
                        play.runnerOnSecond(),
                        play.runnerOnThird(),

                        copyList(play.factTags())
                ))
                .toList();
    }

    /**
     * 선수 정보를 ai-service HTTP DTO로 변환합니다.
     */
    private AiFinalHeadlineRequest.PlayerInfo toPlayerInfo(
            FinalHeadlineContext.PlayerInfo player
    ) {
        if (player == null) {
            return null;
        }

        return new AiFinalHeadlineRequest.PlayerInfo(
                player.id(),
                player.name()
        );
    }

    /**
     * Instant를 JSON에서 다루기 쉬운 ISO-8601 문자열로 변환합니다.
     */
    private String toIsoString(
            Instant instant
    ) {
        return instant == null ? null : instant.toString();
    }

    /**
     * 리스트 필드를 불변 복사본으로 정규화합니다.
     */
    private <T> List<T> copyList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return List.copyOf(values);
    }
}
