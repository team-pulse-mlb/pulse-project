package com.pulse.api;

import com.pulse.common.ai.*;
import com.pulse.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AiCopyContextReader 구현. AI 문구 생성에 노출 가능한 값만 조립하고
 * contextHash를 함께 계산해 반환한다(계산 주체는 예은 측 단일 지점).
 * PROTECTED 컨텍스트에는 점수·초/말·선수명·payload를 절대 포함하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class AiCopyContextService implements AiCopyContextReader {

    private static final int MAX_KEY_MOMENTS = 8;
    private static final int MAX_KEY_MOMENTS_PER_INNING = 2;
    private static final int MAX_KEY_MOMENTS_PER_LABEL = 2;

    private static final int MAX_CONTRIBUTING_LABELS = 4;
    private static final int MAX_REVEALED_MOMENTS = 4;
    private static final int MAX_REVEALED_EVENTS = 8;
    private static final int MAX_VERIFIED_PLAYS = 20;
    private static final List<String> REVEALED_MOMENT_EVENT_TYPES = List.of(
            "scoring_play", "home_run", "lead_change", "big_inning");
    private static final Comparator<RevealedMomentCandidate> REVEALED_MOMENT_ORDER =
            Comparator.comparing(RevealedMomentCandidate::inning,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingInt(candidate -> inningHalfOrder(candidate.inningType()))
                    .thenComparing(candidate -> candidate.source().sourceRef(),
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(RevealedMomentCandidate::id,
                            Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Map<String, Set<String>> EVIDENCE_KEYS = Map.ofEntries(
            Map.entry("pressure_bases_loaded", Set.of("outs", "balls", "strikes")),
            Map.entry("pressure_scoring_position", Set.of("outs", "balls", "strikes")),
            Map.entry("full_count_two_out", Set.of("outs", "balls", "strikes")),
            Map.entry("long_at_bat", Set.of("pitchNumber")),
            Map.entry("pitcher_instability", Set.of("pitcherPitchCount", "velocityDropMph")),
            Map.entry("hard_contact", Set.of("isBarrel", "exitVelocity")),
            Map.entry("scoring_play", Set.of("scoreValue")),
            Map.entry("home_run", Set.of("scoreValue")),
            Map.entry("big_inning", Set.of("scoringPlays")),
            Map.entry("lead_change", Set.of())
    );

    /**
     * 보호 모드에 넘겨도 안전한 상황 근거만 담은 화이트리스트.
     * 카운트·아웃·주자·투구수처럼 '진행 상황'만 포함하고, 결과를 암시하는 값
     * (exitVelocity·isBarrel·scoreValue·velocityDropMph 등)은 제외한다.
     */
    private static final Map<String, Set<String>> PROTECTED_EVIDENCE_KEYS = Map.ofEntries(
            Map.entry("pressure_bases_loaded",
                    Set.of("outs", "runnerOnFirst", "runnerOnSecond", "runnerOnThird")),
            Map.entry("pressure_scoring_position",
                    Set.of("outs", "runnerOnSecond", "runnerOnThird")),
            Map.entry("full_count_two_out",
                    Set.of("outs", "balls", "strikes", "runnerOnFirst", "runnerOnSecond", "runnerOnThird")),
            Map.entry("long_at_bat", Set.of("pitchNumber")),
            Map.entry("pitcher_instability", Set.of("pitcherPitchCount")),
            Map.entry("hard_contact", Set.of("outs", "runnerOnFirst", "runnerOnSecond", "runnerOnThird"))
    );

    private final GameRepository gameRepository;
    private final GameEventRepository gameEventRepository;
    private final WatchScoreRepository watchScoreRepository;
    private final PlayerRepository playerRepository;
    private final PlayRepository playRepository;

    @Override
    public Optional<FinalHeadlineContext> finalHeadlineContext(long gameId, AiCopyMode mode) {
        if (mode == null) {
            return Optional.empty();
        }
        Game game = gameRepository.findById(gameId).filter(Game::isFinal).orElse(null);
        if (game == null) {
            return Optional.empty();
        }
        return mode == AiCopyMode.PROTECTED
                ? Optional.of(protectedFinalHeadlineContext(game))
                : Optional.of(revealedFinalHeadlineContext(game));
    }

    private FinalHeadlineContext protectedFinalHeadlineContext(Game game) {
        long gameId = game.getId();
        WatchScore latestScore = watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(gameId).orElse(null);
        List<String> reasonTags = latestScore == null || latestScore.getTags() == null
                ? List.of() : List.copyOf(latestScore.getTags());
        List<String> signals = positiveSignalKeys(
                latestScore == null ? null : latestScore.getSignalContributions());
        List<FinalHeadlineContext.KeyMoment> keyMoments = selectKeyMoments(
                gameEventRepository.findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                        gameId, GameEvent.SPOILER_PROTECTED_SAFE));
        Map<String, Object> safeContext = new LinkedHashMap<>();
        safeContext.put("gameId", gameId);
        safeContext.put("mode", AiCopyMode.PROTECTED);
        safeContext.put("status", game.getStatus());
        safeContext.put("periodLabel", "경기 종료");
        safeContext.put("reasonTags", reasonTags);
        safeContext.put("spoilerSafeSignals", signals);
        safeContext.put("keyMoments", keyMoments);
        String hash = AiContextHashCalculator.calculate(
                "FINAL_HEADLINE", AiCopyMode.PROTECTED, gameId, null, safeContext);
        return new FinalHeadlineContext(gameId, AiCopyMode.PROTECTED, game.getStatus(), "경기 종료",
                reasonTags, signals, keyMoments, null, null, null, null, null, null, List.of(), hash);
    }

    private FinalHeadlineContext revealedFinalHeadlineContext(Game game) {
        long gameId = game.getId();
        FinalHeadlineContext.Teams teams = new FinalHeadlineContext.Teams(
                new FinalHeadlineContext.Team(game.getHomeTeamName(), game.getHomeTeamAbbr()),
                new FinalHeadlineContext.Team(game.getAwayTeamName(), game.getAwayTeamAbbr()));
        FinalHeadlineContext.FinalScore finalScore =
                new FinalHeadlineContext.FinalScore(game.getHomeRuns(), game.getAwayRuns());
        Integer inningsPlayed = game.getPeriod();
        Boolean extraInnings = inningsPlayed == null ? null : inningsPlayed > 9;
        String winner = winner(game);
        List<Play> orderedPlays = playRepository.findByGameIdOrderByPlayOrderAsc(gameId);
        List<Integer> homeInningScores = safeInningScores(game.getHomeInningScores());
        List<Integer> awayInningScores = safeInningScores(game.getAwayInningScores());
        GameFlowAnalysis gameFlow = analyzeGameFlow(
                game, winner, inningsPlayed, extraInnings, orderedPlays);
        FinalHeadlineContext.SummaryFacts summaryFacts = gameFlow.summaryFacts();
        List<FinalHeadlineContext.RevealedEvent> revealedEvents = revealedEvents(game);
        List<FinalHeadlineContext.VerifiedPlay> verifiedPlays =
                verifiedPlays(orderedPlays, gameFlow);
        List<FinalHeadlineContext.RevealedMoment> revealedMoments = revealedMoments(game);

        Map<String, Object> safeContext = new LinkedHashMap<>();
        safeContext.put("gameId", gameId);
        safeContext.put("mode", AiCopyMode.REVEALED);
        safeContext.put("status", game.getStatus());
        safeContext.put("periodLabel", "경기 종료");
        safeContext.put("teams", teams);
        safeContext.put("finalScore", finalScore);
        safeContext.put("winner", winner);
        safeContext.put("inningsPlayed", inningsPlayed);
        safeContext.put("extraInnings", extraInnings);
        safeContext.put("postseason", game.getPostseason());
        safeContext.put("venue", game.getVenue());
        safeContext.put("startTime", safeStartTime(game.getStartTime()));
        safeContext.put("homeInningScores", homeInningScores);
        safeContext.put("awayInningScores", awayInningScores);
        safeContext.put("summaryFacts", summaryFacts);
        safeContext.put("revealedEvents", revealedEvents);
        safeContext.put("verifiedPlays", verifiedPlays);
        safeContext.put("revealedMoments", revealedMoments);

        String hash = AiContextHashCalculator.calculate(
                "FINAL_HEADLINE", AiCopyMode.REVEALED, gameId, null, safeContext);

        return new FinalHeadlineContext(
                gameId,
                AiCopyMode.REVEALED,
                game.getStatus(),
                "경기 종료",
                List.of(),
                List.of(),
                List.of(),
                teams,
                finalScore,
                winner,
                inningsPlayed,
                extraInnings,
                game.getPostseason(),
                revealedMoments,
                game.getVenue(),
                game.getStartTime(),
                homeInningScores,
                awayInningScores,
                summaryFacts,
                revealedEvents,
                verifiedPlays,
                hash
        );
    }

    /**
     * 경기 전체 점수 진행을 분석해 FINAL_HEADLINE에서 검증 가능한 사실만 계산합니다.
     *
     * <p>마지막 점수 변화가 games 최종 점수와 일치할 때만
     * 선취·동점·리드 교체·결정 이닝·역전·끝내기 값을 확정합니다.
     * 불완전한 play 데이터로 경기 흐름을 추측하지 않습니다.</p>
     */
    private static GameFlowAnalysis analyzeGameFlow(
            Game game,
            String winnerSide,
            Integer finalInning,
            Boolean extraInnings,
            List<Play> orderedPlays
    ) {
        Integer homeRuns = game.getHomeRuns();
        Integer awayRuns = game.getAwayRuns();
        boolean scoreKnown = homeRuns != null && awayRuns != null;
        String loserSide = oppositeSide(winnerSide);

        List<ScoreSnapshot> snapshots = scoreSnapshots(orderedPlays);
        boolean completeTimeline = scoreKnown
                && !snapshots.isEmpty()
                && Objects.equals(snapshots.get(snapshots.size() - 1).homeScore(), homeRuns)
                && Objects.equals(snapshots.get(snapshots.size() - 1).awayScore(), awayRuns);
        List<ScoreSnapshot> verifiedSnapshots = completeTimeline ? snapshots : List.of();

        ScoreSnapshot firstScoringSnapshot = verifiedSnapshots.isEmpty()
                ? null
                : verifiedSnapshots.get(0);
        String firstScoringSide = scoringSide(firstScoringSnapshot);
        Integer firstScoringInning = firstScoringSide == null
                ? null
                : firstScoringSnapshot.inning();

        Set<Long> tyingPlayOrders = new LinkedHashSet<>();
        Integer tyingInning = null;
        for (ScoreSnapshot snapshot : verifiedSnapshots) {
            if (snapshot.previousHomeScore() != snapshot.previousAwayScore()
                    && snapshot.homeScore() == snapshot.awayScore()) {
                if (snapshot.playOrder() != null) {
                    tyingPlayOrders.add(snapshot.playOrder());
                }
                tyingInning = snapshot.inning();
            }
        }

        Set<Long> leadChangePlayOrders = new LinkedHashSet<>();
        String previousNonTiedLeader = null;
        for (ScoreSnapshot snapshot : verifiedSnapshots) {
            String currentLeader = leader(snapshot.homeScore(), snapshot.awayScore());
            if (currentLeader == null) {
                continue;
            }
            if (previousNonTiedLeader != null
                    && !previousNonTiedLeader.equals(currentLeader)
                    && snapshot.playOrder() != null) {
                leadChangePlayOrders.add(snapshot.playOrder());
            }
            previousNonTiedLeader = currentLeader;
        }

        /*
         * 각 득점 플레이 직전·직후 점수를 비교해
         * 리드 획득, 격차 축소, 득점 후 우세·열세를 확정합니다.
         */
        Set<Long> takesLeadPlayOrders = new LinkedHashSet<>();
        Set<Long> leadsAfterPlayOrders = new LinkedHashSet<>();
        Set<Long> trailsAfterPlayOrders = new LinkedHashSet<>();
        Set<Long> cutsDeficitPlayOrders = new LinkedHashSet<>();

        for (ScoreSnapshot snapshot : verifiedSnapshots) {
            String scoringSide = scoringSide(snapshot);
            Long playOrder = snapshot.playOrder();

            if (scoringSide == null || playOrder == null) {
                continue;
            }

            String previousLeader = leader(
                    snapshot.previousHomeScore(),
                    snapshot.previousAwayScore()
            );
            String currentLeader = leader(
                    snapshot.homeScore(),
                    snapshot.awayScore()
            );

            int previousScoringTeamScore =
                    "home".equals(scoringSide)
                            ? snapshot.previousHomeScore()
                            : snapshot.previousAwayScore();
            int previousOpponentScore =
                    "home".equals(scoringSide)
                            ? snapshot.previousAwayScore()
                            : snapshot.previousHomeScore();
            int currentScoringTeamScore =
                    "home".equals(scoringSide)
                            ? snapshot.homeScore()
                            : snapshot.awayScore();
            int currentOpponentScore =
                    "home".equals(scoringSide)
                            ? snapshot.awayScore()
                            : snapshot.homeScore();

            if (scoringSide.equals(currentLeader)) {
                leadsAfterPlayOrders.add(playOrder);

                if (!scoringSide.equals(previousLeader)) {
                    takesLeadPlayOrders.add(playOrder);
                }

                continue;
            }

            if (currentLeader == null) {
                continue;
            }

            trailsAfterPlayOrders.add(playOrder);

            int previousDeficit =
                    previousOpponentScore - previousScoringTeamScore;
            int currentDeficit =
                    currentOpponentScore - currentScoringTeamScore;

            if (previousDeficit > currentDeficit
                    && currentDeficit > 0) {
                cutsDeficitPlayOrders.add(playOrder);
            }
        }

        ScoreSnapshot decisiveSnapshot = decisiveSnapshot(
                verifiedSnapshots,
                winnerSide
        );
        Integer decisiveInning =
                decisiveSnapshot == null
                        ? null
                        : decisiveSnapshot.inning();
        Integer decisiveRuns = decisiveRuns(
                game,
                decisiveSnapshot,
                winnerSide
        );

        /*
         * 결승 득점 이후 승리 팀이 리드를 더 벌린 득점을
         * INSURANCE_SCORE로 확정합니다.
         */
        Set<Long> insuranceScorePlayOrders =
                new LinkedHashSet<>();

        if (decisiveSnapshot != null
                && decisiveSnapshot.playOrder() != null
                && winnerSide != null) {
            for (ScoreSnapshot snapshot : verifiedSnapshots) {
                if (snapshot.playOrder() == null
                        || snapshot.playOrder()
                        <= decisiveSnapshot.playOrder()
                        || !winnerSide.equals(scoringSide(snapshot))
                        || !winnerSide.equals(
                                leader(
                                        snapshot.homeScore(),
                                        snapshot.awayScore()
                                )
                        )) {
                    continue;
                }

                int previousWinnerScore =
                        "home".equals(winnerSide)
                                ? snapshot.previousHomeScore()
                                : snapshot.previousAwayScore();
                int previousLoserScore =
                        "home".equals(winnerSide)
                                ? snapshot.previousAwayScore()
                                : snapshot.previousHomeScore();
                int currentWinnerScore =
                        "home".equals(winnerSide)
                                ? snapshot.homeScore()
                                : snapshot.awayScore();
                int currentLoserScore =
                        "home".equals(winnerSide)
                                ? snapshot.awayScore()
                                : snapshot.homeScore();

                int previousMargin =
                        previousWinnerScore - previousLoserScore;
                int currentMargin =
                        currentWinnerScore - currentLoserScore;

                if (currentMargin > previousMargin) {
                    insuranceScorePlayOrders.add(
                            snapshot.playOrder()
                    );
                }
            }
        }

        Boolean comebackWin = verifiedSnapshots.isEmpty() || winnerSide == null
                ? null
                : verifiedSnapshots.stream()
                .map(snapshot -> leader(snapshot.homeScore(), snapshot.awayScore()))
                .anyMatch(oppositeSide(winnerSide)::equals);

        Boolean walkOff = verifiedSnapshots.isEmpty() || winnerSide == null
                ? null
                : isWalkOff(game, winnerSide, decisiveSnapshot, verifiedSnapshots);

        Boolean shutout = !scoreKnown || winnerSide == null
                ? null
                : Objects.equals(scoreBySide(game, loserSide), 0);

        FinalHeadlineContext.SummaryFacts summaryFacts = new FinalHeadlineContext.SummaryFacts(
                winnerSide,
                teamNameBySide(game, winnerSide),
                teamNameBySide(game, loserSide),
                scoreBySide(game, winnerSide),
                scoreBySide(game, loserSide),

                firstScoringSide,
                firstScoringInning,

                tyingInning,
                decisiveInning,
                decisiveRuns,

                completeTimeline ? leadChangePlayOrders.size() : null,
                comebackWin,
                walkOff,
                shutout,
                extraInnings,
                finalInning,

                scoreKnown ? Math.abs(homeRuns - awayRuns) : null,
                scoreKnown ? homeRuns + awayRuns : null
        );

        return new GameFlowAnalysis(
                summaryFacts,
                firstScoringSnapshot == null
                        ? null
                        : firstScoringSnapshot.playOrder(),
                tyingPlayOrders,
                leadChangePlayOrders,
                takesLeadPlayOrders,
                leadsAfterPlayOrders,
                trailsAfterPlayOrders,
                cutsDeficitPlayOrders,
                decisiveSnapshot == null
                        ? null
                        : decisiveSnapshot.playOrder(),
                insuranceScorePlayOrders
        );
    }

    /**
     * play별 누적 점수를 실제 점수 변화 snapshot으로 변환합니다.
     * 점수가 감소하는 비정상 행은 경기 흐름 계산에서 제외합니다.
     */
    private static List<ScoreSnapshot> scoreSnapshots(
            List<Play> orderedPlays
    ) {
        if (orderedPlays == null || orderedPlays.isEmpty()) {
            return List.of();
        }

        List<Play> safePlays = orderedPlays.stream()
                .filter(Objects::nonNull)
                .filter(play -> play.getPlayOrder() != null)
                .sorted(Comparator.comparing(Play::getPlayOrder))
                .toList();

        List<ScoreSnapshot> snapshots = new ArrayList<>();
        int previousHomeScore = 0;
        int previousAwayScore = 0;

        for (Play play : safePlays) {
            Integer homeScore = play.getHomeScore();
            Integer awayScore = play.getAwayScore();
            if (homeScore == null || awayScore == null
                    || homeScore < previousHomeScore
                    || awayScore < previousAwayScore) {
                continue;
            }
            if (homeScore == previousHomeScore && awayScore == previousAwayScore) {
                continue;
            }

            snapshots.add(new ScoreSnapshot(
                    play.getPlayOrder(),
                    play.getInning(),
                    play.getInningType(),
                    previousHomeScore,
                    previousAwayScore,
                    homeScore,
                    awayScore,
                    homeScore - previousHomeScore,
                    awayScore - previousAwayScore
            ));
            previousHomeScore = homeScore;
            previousAwayScore = awayScore;
        }

        return List.copyOf(snapshots);
    }

    private static String scoringSide(
            ScoreSnapshot snapshot
    ) {
        if (snapshot == null) {
            return null;
        }
        if (snapshot.homeRunsScored() > 0 && snapshot.awayRunsScored() == 0) {
            return "home";
        }
        if (snapshot.awayRunsScored() > 0 && snapshot.homeRunsScored() == 0) {
            return "away";
        }
        return null;
    }

    private static String leader(
            int homeScore,
            int awayScore
    ) {
        if (homeScore == awayScore) {
            return null;
        }
        return homeScore > awayScore ? "home" : "away";
    }

    /**
     * 최종 승리 팀이 이후 한 번도 동점이나 열세를 허용하지 않은
     * 최초 점수 변화를 결정 득점으로 판정합니다.
     */
    private static ScoreSnapshot decisiveSnapshot(
            List<ScoreSnapshot> snapshots,
            String winnerSide
    ) {
        if (snapshots == null || snapshots.isEmpty() || winnerSide == null) {
            return null;
        }

        for (int index = 0; index < snapshots.size(); index++) {
            ScoreSnapshot candidate = snapshots.get(index);
            if (!winnerSide.equals(leader(candidate.homeScore(), candidate.awayScore()))) {
                continue;
            }

            boolean remainsAhead = true;
            for (int laterIndex = index; laterIndex < snapshots.size(); laterIndex++) {
                ScoreSnapshot later = snapshots.get(laterIndex);
                if (!winnerSide.equals(leader(later.homeScore(), later.awayScore()))) {
                    remainsAhead = false;
                    break;
                }
            }
            if (remainsAhead) {
                return candidate;
            }
        }

        return null;
    }

    private static Integer decisiveRuns(
            Game game,
            ScoreSnapshot decisiveSnapshot,
            String winnerSide
    ) {
        if (decisiveSnapshot == null || winnerSide == null) {
            return null;
        }

        List<Integer> inningScores = "home".equals(winnerSide)
                ? game.getHomeInningScores()
                : game.getAwayInningScores();
        Integer inning = decisiveSnapshot.inning();
        if (inningScores != null && inning != null
                && inning > 0 && inning <= inningScores.size()) {
            Integer inningRuns = inningScores.get(inning - 1);
            if (inningRuns != null) {
                return inningRuns;
            }
        }

        return "home".equals(winnerSide)
                ? decisiveSnapshot.homeRunsScored()
                : decisiveSnapshot.awayRunsScored();
    }

    private static boolean isWalkOff(
            Game game,
            String winnerSide,
            ScoreSnapshot decisiveSnapshot,
            List<ScoreSnapshot> snapshots
    ) {
        if (!"home".equals(winnerSide)
                || decisiveSnapshot == null
                || decisiveSnapshot.inning() == null
                || decisiveSnapshot.inning() < 9
                || !"bottom".equalsIgnoreCase(decisiveSnapshot.inningType())
                || snapshots.isEmpty()) {
            return false;
        }

        ScoreSnapshot lastSnapshot = snapshots.get(snapshots.size() - 1);
        return Objects.equals(decisiveSnapshot.playOrder(), lastSnapshot.playOrder())
                && Objects.equals(game.getHomeRuns(), decisiveSnapshot.homeScore())
                && Objects.equals(game.getAwayRuns(), decisiveSnapshot.awayScore())
                && decisiveSnapshot.homeScore() > decisiveSnapshot.awayScore();
    }

    private static String oppositeSide(
            String side
    ) {
        if ("home".equals(side)) {
            return "away";
        }

        if ("away".equals(side)) {
            return "home";
        }

        return null;
    }

    private static String teamNameBySide(
            Game game,
            String side
    ) {
        if ("home".equals(side)) {
            return game.getHomeTeamName();
        }

        if ("away".equals(side)) {
            return game.getAwayTeamName();
        }

        return null;
    }

    private static Integer scoreBySide(
            Game game,
            String side
    ) {
        if ("home".equals(side)) {
            return game.getHomeRuns();
        }

        if ("away".equals(side)) {
            return game.getAwayRuns();
        }

        return null;
    }

    private static List<Integer> safeInningScores(
            List<Integer> inningScores
    ) {
        if (inningScores == null || inningScores.isEmpty()) {
            return List.of();
        }

        return List.copyOf(inningScores);
    }

    private static String safeStartTime(
            Instant startTime
    ) {
        return startTime == null ? null : startTime.toString();
    }

    private List<FinalHeadlineContext.VerifiedPlay> verifiedPlays(
            List<Play> orderedPlays,
            GameFlowAnalysis gameFlow
    ) {
        List<Play> selectedPlays = selectVerifiedPlayCandidates(orderedPlays, gameFlow);

        if (selectedPlays.isEmpty()) {
            return List.of();
        }

        List<Long> playerIds = selectedPlays.stream()
                .flatMap(play -> java.util.stream.Stream.of(play.getBatterId(), play.getPitcherId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, FinalHeadlineContext.PlayerInfo> playersById = playerInfosById(playerIds);

        return selectedPlays.stream()
                .map(play -> new FinalHeadlineContext.VerifiedPlay(
                        play.getId(),
                        play.getPlayOrder(),

                        play.getInning(),
                        play.getInningType(),

                        play.getText(),
                        play.getTextKo(),

                        play.getHomeScore(),
                        play.getAwayScore(),

                        play.getScoringPlay(),
                        play.getScoreValue(),

                        play.getOuts(),
                        play.getBalls(),
                        play.getStrikes(),

                        // 선수 ID가 없는 플레이도 FINAL_HEADLINE 근거로 사용할 수 있으므로
                        // null ID로 불변 Map을 조회하지 않고 선수 정보만 null로 전달합니다.
                        play.getBatterId() == null
                                ? null
                                : playersById.get(play.getBatterId()),
                        play.getPitcherId() == null
                                ? null
                                : playersById.get(play.getPitcherId()),

                        play.getRunnerOnFirst(),
                        play.getRunnerOnSecond(),
                        play.getRunnerOnThird(),

                        factTags(play, gameFlow)
                ))
                .toList();
    }

    private static List<Play> selectVerifiedPlayCandidates(
            List<Play> candidates,
            GameFlowAnalysis gameFlow
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<Play> usablePlays = candidates.stream()
                .filter(AiCopyContextService::isUsableVerifiedPlay)
                .sorted(Comparator.comparing(Play::getPlayOrder))
                .toList();
        Map<Long, Play> selectedByKey = new LinkedHashMap<>();

        // 결정 득점·리드 교체·동점·선취 득점은 일반 득점 플레이보다 먼저 보존합니다.
        for (Long playOrder : gameFlow.importantPlayOrders()) {
            if (selectedByKey.size() >= MAX_VERIFIED_PLAYS) {
                break;
            }
            usablePlays.stream()
                    .filter(play -> Objects.equals(play.getPlayOrder(), playOrder))
                    .findFirst()
                    .ifPresent(play -> selectedByKey.putIfAbsent(verifiedPlayKey(play), play));
        }

        // 경기 스토리의 핵심 근거인 득점 플레이를 우선 포함합니다.
        for (Play play : usablePlays) {
            if (selectedByKey.size() >= MAX_VERIFIED_PLAYS) {
                break;
            }
            if (Boolean.TRUE.equals(play.getScoringPlay())) {
                selectedByKey.putIfAbsent(verifiedPlayKey(play), play);
            }
        }

        // 남은 공간에는 최근 플레이를 넣어 결정 이닝 전후 맥락을 보완합니다.
        for (int index = usablePlays.size() - 1;
             index >= 0 && selectedByKey.size() < MAX_VERIFIED_PLAYS;
             index--) {
            Play play = usablePlays.get(index);
            selectedByKey.putIfAbsent(verifiedPlayKey(play), play);
        }

        return selectedByKey.values().stream()
                .sorted(Comparator.comparing(Play::getPlayOrder))
                .toList();
    }

    private static boolean isUsableVerifiedPlay(
            Play play
    ) {
        return play != null
                && play.getId() != null
                && play.getPlayOrder() != null
                && play.getInning() != null
                && play.getInning() > 0
                && play.getText() != null
                && !play.getText().isBlank();
    }

    private static Long verifiedPlayKey(
            Play play
    ) {
        return play.getPlayOrder() == null ? play.getId() : play.getPlayOrder();
    }

    /**
     * 원문 또는 검수된 한국어 번역에서 실제 안타 결과를 확인합니다.
     *
     * <p>단순 득점 플레이만으로 결승타·동점타·적시타를
     * 추측하지 않도록 별도 HIT 근거를 생성합니다.</p>
     */
    private static boolean isHitPlay(
            Play play
    ) {
        String sourceText =
                play.getText() == null
                        ? ""
                        : play.getText().toLowerCase(Locale.ROOT);
        String translatedText =
                play.getTextKo() == null
                        ? ""
                        : play.getTextKo();

        return sourceText.contains("singled")
                || sourceText.contains("doubled")
                || sourceText.contains("tripled")
                || sourceText.contains("homered")
                || sourceText.contains("home run")
                || translatedText.contains("안타")
                || translatedText.contains("2루타")
                || translatedText.contains("3루타")
                || translatedText.contains("홈런");
    }

    private static List<String> factTags(
            Play play,
            GameFlowAnalysis gameFlow
    ) {
        List<String> tags = new ArrayList<>();
        Long playOrder = play.getPlayOrder();

        if (Boolean.TRUE.equals(play.getScoringPlay())) {
            tags.add("SCORING_PLAY");
        }

        if (play.getScoreValue() != null
                && play.getScoreValue() > 0) {
            tags.add("RUNS_SCORED");
        }

        if (play.getHomeScore() != null
                || play.getAwayScore() != null) {
            tags.add("SCORE_AFTER");
        }

        if (play.getTextKo() != null
                && !play.getTextKo().isBlank()) {
            tags.add("TRANSLATED");
        }

        if (isHitPlay(play)) {
            tags.add("HIT");
        }

        if (Objects.equals(
                gameFlow.firstScoringPlayOrder(),
                playOrder
        )) {
            tags.add("FIRST_SCORE");
        }

        if (gameFlow.tyingPlayOrders().contains(playOrder)) {
            tags.add("TYING_SCORE");
        }

        if (gameFlow.leadChangePlayOrders().contains(playOrder)) {
            tags.add("LEAD_CHANGE");
        }

        if (gameFlow.takesLeadPlayOrders().contains(playOrder)) {
            tags.add("TAKES_LEAD");
        }

        if (gameFlow.leadsAfterPlayOrders().contains(playOrder)) {
            tags.add("LEADS_AFTER");
        }

        if (gameFlow.trailsAfterPlayOrders().contains(playOrder)) {
            tags.add("TRAILS_AFTER");
        }

        if (gameFlow.cutsDeficitPlayOrders().contains(playOrder)) {
            tags.add("CUTS_DEFICIT");
        }

        if (Objects.equals(
                gameFlow.decisivePlayOrder(),
                playOrder
        )) {
            tags.add("DECISIVE_SCORE");

            if (Boolean.TRUE.equals(
                    gameFlow.summaryFacts().comebackWin()
            )) {
                tags.add("COMEBACK_WIN");
            }

            if (Boolean.TRUE.equals(
                    gameFlow.summaryFacts().walkOff()
            )) {
                tags.add("WALK_OFF");
            }
        }

        if (gameFlow.insuranceScorePlayOrders().contains(playOrder)) {
            tags.add("INSURANCE_SCORE");
        }

        return List.copyOf(tags);
    }

    private List<FinalHeadlineContext.RevealedEvent> revealedEvents(Game game) {
        List<GameEvent> events = gameEventRepository
                .findByGameIdAndSpoilerLevelAndEventTypeInOrderByInningAscSourceRefAscIdAsc(
                        game.getId(), GameEvent.SPOILER_REVEALED_ONLY, REVEALED_MOMENT_EVENT_TYPES);
        if (events.isEmpty()) {
            return List.of();
        }

        List<Long> playerIds = events.stream()
                .flatMap(event -> java.util.stream.Stream.of(event.getBatterId(), event.getPitcherId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, FinalHeadlineContext.PlayerInfo> playersById = playerInfosById(playerIds);

        return events.stream()
                .limit(MAX_REVEALED_EVENTS)
                .map(event -> new FinalHeadlineContext.RevealedEvent(
                        event.getId(),
                        event.getEventType(),
                        event.getInning(),
                        event.getInningType(),
                        playersById.get(event.getBatterId()),
                        playersById.get(event.getPitcherId()),
                        projectEvidence(event.getEventType(), event.getPayload())
                ))
                .toList();
    }

    private Map<Long, FinalHeadlineContext.PlayerInfo> playerInfosById(
            List<Long> playerIds
    ) {
        if (playerIds == null || playerIds.isEmpty()) {
            return Map.of();
        }

        return playerRepository.findAllById(playerIds).stream()
                .filter(player -> player.getFullName() != null && !player.getFullName().isBlank())
                .collect(Collectors.toMap(
                        Player::getId,
                        player -> new FinalHeadlineContext.PlayerInfo(
                                player.getId(),
                                player.getFullName()
                        ),
                        (first, ignored) -> first
                ));
    }

    private List<FinalHeadlineContext.RevealedMoment> revealedMoments(Game game) {
        List<GameEvent> events = gameEventRepository
                .findByGameIdAndSpoilerLevelAndEventTypeInOrderByInningAscSourceRefAscIdAsc(
                        game.getId(), GameEvent.SPOILER_REVEALED_ONLY, REVEALED_MOMENT_EVENT_TYPES);
        Map<SourceKey, RevealedMomentAccumulator> grouped = new LinkedHashMap<>();
        for (GameEvent event : events) {
            if (!GameEvent.SOURCE_TYPE_PLAY.equals(event.getSourceType())
                    || event.getSourceRef() == null
                    || !REVEALED_MOMENT_EVENT_TYPES.contains(event.getEventType())) {
                continue;
            }
            grouped.computeIfAbsent(
                            new SourceKey(event.getSourceType(), event.getSourceRef()),
                            ignored -> new RevealedMomentAccumulator(event))
                    .add(event);
        }
        if (grouped.isEmpty()) {
            return List.of();
        }

        List<Long> playOrders = grouped.keySet().stream().map(SourceKey::sourceRef).distinct().toList();
        Map<Long, Play> playsByOrder = playRepository.findByGameIdAndPlayOrderIn(game.getId(), playOrders)
                .stream()
                .collect(Collectors.toMap(Play::getPlayOrder, Function.identity(), (first, ignored) -> first));
        List<Long> batterIds = grouped.values().stream()
                .map(RevealedMomentAccumulator::batterId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> playerNames = playerRepository.findAllById(batterIds).stream()
                .filter(player -> player.getFullName() != null && !player.getFullName().isBlank())
                .collect(Collectors.toMap(Player::getId, Player::getFullName, (first, ignored) -> first));

        List<RevealedMomentCandidate> candidates = grouped.entrySet().stream()
                .map(entry -> entry.getValue().toCandidate(
                        playsByOrder.get(entry.getKey().sourceRef()), playerNames))
                .toList();
        return selectRevealedMoments(candidates).stream()
                .map(candidate -> candidate.toContext(game))
                .toList();
    }

    private static List<RevealedMomentCandidate> selectRevealedMoments(
            List<RevealedMomentCandidate> candidates
    ) {
        Map<SourceKey, RevealedMomentCandidate> selected = new LinkedHashMap<>();
        latestWithType(candidates, "lead_change").ifPresent(candidate -> selected.put(candidate.source(), candidate));
        largestWithType(candidates, "home_run", RevealedMomentCandidate::runsScored)
                .ifPresent(candidate -> selected.put(candidate.source(), candidate));
        largestWithType(candidates, "big_inning", candidate -> candidate.scoringPlays() == null
                ? null : Math.toIntExact(candidate.scoringPlays()))
                .ifPresent(candidate -> selected.put(candidate.source(), candidate));
        latestWithType(candidates, "scoring_play").ifPresent(candidate -> selected.put(candidate.source(), candidate));
        return selected.values().stream()
                .sorted(REVEALED_MOMENT_ORDER)
                .limit(MAX_REVEALED_MOMENTS)
                .toList();
    }

    private static Optional<RevealedMomentCandidate> latestWithType(
            List<RevealedMomentCandidate> candidates,
            String eventType
    ) {
        return candidates.stream()
                .filter(candidate -> candidate.eventTypes().contains(eventType))
                .max(REVEALED_MOMENT_ORDER);
    }

    private static Optional<RevealedMomentCandidate> largestWithType(
            List<RevealedMomentCandidate> candidates,
            String eventType,
            Function<RevealedMomentCandidate, Integer> size
    ) {
        return candidates.stream()
                .filter(candidate -> candidate.eventTypes().contains(eventType))
                .max(Comparator.comparingInt((RevealedMomentCandidate candidate) ->
                                Optional.ofNullable(size.apply(candidate)).orElse(-1))
                        .thenComparing(REVEALED_MOMENT_ORDER));
    }

    @Override
    public Optional<EventCopyContext> eventCopyContext(long gameId, long eventId, AiCopyMode mode) {
        if (mode == null) {
            return Optional.empty();
        }
        GameEvent event = gameEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getGameId() == null || event.getGameId() != gameId) {
            return Optional.empty();
        }
        String label = mode == AiCopyMode.PROTECTED
                ? GameEventLabelPolicy.protectedLabel(event.getSpoilerLevel(), event.getEventType())
                : GameEventLabelPolicy.revealedLabel(event.getSpoilerLevel(), event.getEventType());
        if (label == null) {
            return Optional.empty();
        }

        Map<String, Object> safeContext = new LinkedHashMap<>();
        safeContext.put("gameId", gameId);
        safeContext.put("eventId", eventId);
        safeContext.put("eventType", event.getEventType());
        safeContext.put("label", label);
        safeContext.put("inning", event.getInning());
        if (mode == AiCopyMode.PROTECTED) {
            List<String> contributingLabels = protectedContributingLabels(gameId, event);
            Map<String, Object> situation = projectProtectedEvidence(
                    event.getEventType(), event.getPayload());
            if (!contributingLabels.isEmpty()) {
                safeContext.put("contributingLabels", contributingLabels);
            }
            if (!situation.isEmpty()) {
                safeContext.put("situation", situation);
            }
            String hash = AiContextHashCalculator.calculate("EVENT_COPY", mode, gameId, eventId, safeContext);
            return Optional.of(new ProtectedEventCopyContext(
                    gameId, eventId, event.getEventType(), label, event.getInning(),
                    contributingLabels, situation, hash));
        }

        Map<String, Object> evidence = projectEvidence(event.getEventType(), event.getPayload());
        String batter = playerName(event.getBatterId());
        String pitcher = playerName(event.getPitcherId());
        safeContext.put("inningType", event.getInningType());
        safeContext.put("batter", batter);
        safeContext.put("pitcher", pitcher);
        safeContext.put("evidence", evidence);
        String hash = AiContextHashCalculator.calculate("EVENT_COPY", mode, gameId, eventId, safeContext);
        return Optional.of(new RevealedEventCopyContext(gameId, eventId, event.getEventType(), label,
                event.getInning(), hash, event.getInningType(), batter, pitcher, evidence));
    }

    static List<FinalHeadlineContext.KeyMoment> selectKeyMoments(List<GameEvent> events) {
        List<SelectedMoment> selected = new ArrayList<>();
        Map<Integer, Integer> inningCounts = new HashMap<>();
        Map<String, Integer> labelCounts = new HashMap<>();
        Set<MomentKey> duplicates = new HashSet<>();
        List<GameEvent> safeEvents = events == null ? List.of() : events;

        for (int index = safeEvents.size() - 1; index >= 0 && selected.size() < MAX_KEY_MOMENTS; index--) {
            GameEvent event = safeEvents.get(index);
            String label = GameEventLabelPolicy.protectedLabel(event.getSpoilerLevel(), event.getEventType());
            if (label == null) {
                continue;
            }
            MomentKey key = new MomentKey(event.getInning(), label);
            int inningCount = inningCounts.getOrDefault(event.getInning(), 0);
            int labelCount = labelCounts.getOrDefault(label, 0);
            if (duplicates.contains(key)
                    || inningCount >= MAX_KEY_MOMENTS_PER_INNING
                    || labelCount >= MAX_KEY_MOMENTS_PER_LABEL) {
                continue;
            }
            duplicates.add(key);
            inningCounts.put(event.getInning(), inningCount + 1);
            labelCounts.put(label, labelCount + 1);
            selected.add(new SelectedMoment(event.getInning(), label, event.getObservedAt(), event.getId()));
        }
        return selected.stream()
                .sorted(Comparator.comparing(SelectedMoment::inning,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SelectedMoment::observedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SelectedMoment::id, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(moment -> new FinalHeadlineContext.KeyMoment(moment.inning(), moment.label()))
                .toList();
    }

    static Map<String, Object> projectEvidence(String eventType, Map<String, Object> payload) {
        return projectByKeys(EVIDENCE_KEYS.get(eventType), payload);
    }

    /** 보호 모드 상황 근거만 추출한다(결과 암시 값 제외). */
    static Map<String, Object> projectProtectedEvidence(String eventType, Map<String, Object> payload) {
        return projectByKeys(PROTECTED_EVIDENCE_KEYS.get(eventType), payload);
    }

    private static Map<String, Object> projectByKeys(Set<String> allowed, Map<String, Object> payload) {
        if (allowed == null || allowed.isEmpty() || payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        allowed.stream().sorted().forEach(key -> {
            Object value = payload.get(key);
            if (value instanceof Number || value instanceof Boolean) {
                evidence.put(key, value);
            }
        });
        return Map.copyOf(evidence);
    }

    /**
     * 같은 이닝의 서로 다른 보호 라벨을 시간순으로 모은다(anchor 라벨 포함, 최대 {@value #MAX_CONTRIBUTING_LABELS}).
     * 여러 긴장 요소가 겹친 순간을 문구가 반영할 수 있게 한다.
     */
    private List<String> protectedContributingLabels(long gameId, GameEvent anchor) {
        if (anchor.getInning() == null) {
            return List.of();
        }
        List<String> labels = new ArrayList<>();
        for (GameEvent event : gameEventRepository.findByGameIdAndInningAndSpoilerLevelOrderByObservedAtAscIdAsc(
                gameId, anchor.getInning(), GameEvent.SPOILER_PROTECTED_SAFE)) {
            String label = GameEventLabelPolicy.protectedLabel(event.getSpoilerLevel(), event.getEventType());
            if (label != null && !labels.contains(label)) {
                labels.add(label);
                if (labels.size() >= MAX_CONTRIBUTING_LABELS) {
                    break;
                }
            }
        }
        return List.copyOf(labels);
    }

    private String playerName(Long playerId) {
        if (playerId == null) {
            return null;
        }
        return playerRepository.findById(playerId)
                .map(Player::getFullName)
                .filter(name -> !name.isBlank())
                .orElse(null);
    }

    private static List<String> positiveSignalKeys(Map<String, Double> signals) {
        if (signals == null) {
            return List.of();
        }
        return signals.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static String winner(Game game) {
        if (game.getHomeRuns() == null || game.getAwayRuns() == null
                || game.getHomeRuns().equals(game.getAwayRuns())) {
            return null;
        }
        return game.getHomeRuns() > game.getAwayRuns() ? "home" : "away";
    }

    private static int inningHalfOrder(String inningType) {
        if ("top".equalsIgnoreCase(inningType)) {
            return 0;
        }
        if ("bottom".equalsIgnoreCase(inningType)) {
            return 1;
        }
        return 2;
    }

    private static String normalizedInningHalf(String inningType) {
        if ("top".equalsIgnoreCase(inningType)) {
            return "Top";
        }
        if ("bottom".equalsIgnoreCase(inningType)) {
            return "Bottom";
        }
        return null;
    }

    private static Integer integerPayload(GameEvent event, String key) {
        Object value = event.getPayload() == null ? null : event.getPayload().get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Long longPayload(GameEvent event, String key) {
        Object value = event.getPayload() == null ? null : event.getPayload().get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private static <T extends Comparable<? super T>> T larger(T first, T second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.compareTo(second) >= 0 ? first : second;
    }

    private record ScoreSnapshot(
            Long playOrder,
            Integer inning,
            String inningType,
            int previousHomeScore,
            int previousAwayScore,
            int homeScore,
            int awayScore,
            int homeRunsScored,
            int awayRunsScored
    ) {
    }

    private record GameFlowAnalysis(
            FinalHeadlineContext.SummaryFacts summaryFacts,
            Long firstScoringPlayOrder,
            Set<Long> tyingPlayOrders,
            Set<Long> leadChangePlayOrders,
            Set<Long> takesLeadPlayOrders,
            Set<Long> leadsAfterPlayOrders,
            Set<Long> trailsAfterPlayOrders,
            Set<Long> cutsDeficitPlayOrders,
            Long decisivePlayOrder,
            Set<Long> insuranceScorePlayOrders
    ) {
        private GameFlowAnalysis {
            tyingPlayOrders =
                    immutableSet(tyingPlayOrders);
            leadChangePlayOrders =
                    immutableSet(leadChangePlayOrders);
            takesLeadPlayOrders =
                    immutableSet(takesLeadPlayOrders);
            leadsAfterPlayOrders =
                    immutableSet(leadsAfterPlayOrders);
            trailsAfterPlayOrders =
                    immutableSet(trailsAfterPlayOrders);
            cutsDeficitPlayOrders =
                    immutableSet(cutsDeficitPlayOrders);
            insuranceScorePlayOrders =
                    immutableSet(insuranceScorePlayOrders);
        }

        private Set<Long> importantPlayOrders() {
            Set<Long> result = new LinkedHashSet<>();

            if (decisivePlayOrder != null) {
                result.add(decisivePlayOrder);
            }

            result.addAll(insuranceScorePlayOrders);
            result.addAll(leadChangePlayOrders);
            result.addAll(tyingPlayOrders);
            result.addAll(cutsDeficitPlayOrders);
            result.addAll(takesLeadPlayOrders);

            if (firstScoringPlayOrder != null) {
                result.add(firstScoringPlayOrder);
            }

            return Collections.unmodifiableSet(result);
        }

        private static Set<Long> immutableSet(
                Set<Long> values
        ) {
            if (values == null || values.isEmpty()) {
                return Set.of();
            }

            return Collections.unmodifiableSet(
                    new LinkedHashSet<>(values)
            );
        }
    }

    private record SourceKey(String sourceType, Long sourceRef) {
    }

    private static final class RevealedMomentAccumulator {
        private final SourceKey source;
        private final Set<String> eventTypes = new LinkedHashSet<>();
        private Long id;
        private Integer inning;
        private String inningType;
        private Long batterId;
        private Integer runsScored;
        private Long scoringPlays;

        private RevealedMomentAccumulator(GameEvent event) {
            source = new SourceKey(event.getSourceType(), event.getSourceRef());
            id = event.getId();
            inning = event.getInning();
            inningType = event.getInningType();
            batterId = event.getBatterId();
        }

        private void add(GameEvent event) {
            eventTypes.add(event.getEventType());
            if (id == null || event.getId() != null && event.getId() < id) {
                id = event.getId();
            }
            if (inning == null) {
                inning = event.getInning();
            }
            if (inningType == null) {
                inningType = event.getInningType();
            }
            if (batterId == null) {
                batterId = event.getBatterId();
            }
            runsScored = larger(runsScored, integerPayload(event, "scoreValue"));
            scoringPlays = larger(scoringPlays, longPayload(event, "scoringPlays"));
        }

        private Long batterId() {
            return batterId;
        }

        private RevealedMomentCandidate toCandidate(Play play, Map<Long, String> playerNames) {
            List<String> orderedTypes = eventTypes.stream()
                    .sorted(Comparator.comparingInt(REVEALED_MOMENT_EVENT_TYPES::indexOf))
                    .toList();
            return new RevealedMomentCandidate(source, id, inning, inningType, orderedTypes,
                    playerNames.get(batterId), runsScored, scoringPlays, play);
        }
    }

    private record RevealedMomentCandidate(
            SourceKey source,
            Long id,
            Integer inning,
            String inningType,
            List<String> eventTypes,
            String batter,
            Integer runsScored,
            Long scoringPlays,
            Play play
    ) {
        private FinalHeadlineContext.RevealedMoment toContext(Game game) {
            String inningHalf = normalizedInningHalf(inningType);
            String battingTeam = "Top".equals(inningHalf)
                    ? game.getAwayTeamAbbr()
                    : "Bottom".equals(inningHalf) ? game.getHomeTeamAbbr() : null;
            FinalHeadlineContext.ScoreAfter scoreAfter = play == null
                    || play.getHomeScore() == null && play.getAwayScore() == null
                    ? null
                    : new FinalHeadlineContext.ScoreAfter(play.getHomeScore(), play.getAwayScore());
            return new FinalHeadlineContext.RevealedMoment(
                    inning, inningHalf, battingTeam, eventTypes, batter,
                    runsScored, scoreAfter, scoringPlays);
        }
    }

    private record MomentKey(Integer inning, String label) {
    }

    private record SelectedMoment(Integer inning, String label, Instant observedAt, Long id) {
    }
}
