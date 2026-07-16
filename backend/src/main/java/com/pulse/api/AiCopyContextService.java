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
            Map.entry("pitcher_instability", Set.of("pitcherPitchCount"))
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
        List<Integer> homeInningScores = safeInningScores(game.getHomeInningScores());
        List<Integer> awayInningScores = safeInningScores(game.getAwayInningScores());
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
                null,
                List.of(),
                List.of(),
                hash
        );
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