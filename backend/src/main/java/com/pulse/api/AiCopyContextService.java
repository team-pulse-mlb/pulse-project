package com.pulse.api;

import com.pulse.common.ai.AiContextHashCalculator;
import com.pulse.common.ai.AiCopyContextReader;
import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.EventCopyContext;
import com.pulse.common.ai.FinalHeadlineContext;
import com.pulse.common.ai.ProtectedEventCopyContext;
import com.pulse.common.ai.RevealedEventCopyContext;
import com.pulse.domain.Game;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventLabelPolicy;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    @Override
    public Optional<FinalHeadlineContext> finalHeadlineContext(long gameId, AiCopyMode mode) {
        if (mode == null) {
            return Optional.empty();
        }
        Game game = gameRepository.findById(gameId).filter(Game::isFinal).orElse(null);
        if (game == null) {
            return Optional.empty();
        }
        WatchScore latestScore = watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(gameId).orElse(null);
        List<String> reasonTags = latestScore == null || latestScore.getTags() == null
                ? List.of() : List.copyOf(latestScore.getTags());
        List<String> signals = positiveSignalKeys(
                latestScore == null ? null : latestScore.getSignalContributions());
        List<FinalHeadlineContext.KeyMoment> keyMoments = selectKeyMoments(
                gameEventRepository.findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                        gameId, GameEvent.SPOILER_PROTECTED_SAFE));
        FinalHeadlineContext.FinalScore finalScore = mode == AiCopyMode.REVEALED
                ? new FinalHeadlineContext.FinalScore(game.getHomeRuns(), game.getAwayRuns()) : null;
        String winner = mode == AiCopyMode.REVEALED ? winner(game) : null;

        Map<String, Object> safeContext = new LinkedHashMap<>();
        safeContext.put("gameId", gameId);
        safeContext.put("mode", mode);
        safeContext.put("status", game.getStatus());
        safeContext.put("periodLabel", "경기 종료");
        safeContext.put("reasonTags", reasonTags);
        safeContext.put("spoilerSafeSignals", signals);
        safeContext.put("keyMoments", keyMoments);
        safeContext.put("finalScore", finalScore);
        safeContext.put("winner", winner);
        String hash = AiContextHashCalculator.calculate("FINAL_HEADLINE", mode, gameId, null, safeContext);
        return Optional.of(new FinalHeadlineContext(gameId, mode, game.getStatus(), "경기 종료", reasonTags,
                signals, keyMoments, finalScore, winner, hash));
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

    private record MomentKey(Integer inning, String label) {
    }

    private record SelectedMoment(Integer inning, String label, Instant observedAt, Long id) {
    }
}
