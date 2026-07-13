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
            String hash = AiContextHashCalculator.calculate("EVENT_COPY", mode, gameId, eventId, safeContext);
            return Optional.of(new ProtectedEventCopyContext(
                    gameId, eventId, event.getEventType(), label, event.getInning(), hash));
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
        Set<String> allowed = EVIDENCE_KEYS.get(eventType);
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
