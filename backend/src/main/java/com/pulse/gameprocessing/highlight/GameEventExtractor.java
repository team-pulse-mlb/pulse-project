package com.pulse.gameprocessing.highlight;

import com.pulse.gameprocessing.aicopy.AiGenerationTrigger;
import com.pulse.gameprocessing.aicopy.GameEventCopyRequestedEvent;
import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.ScoreTask.PitchSnapshot;
import com.pulse.common.message.ScoreTask.PlateAppearanceSnapshot;
import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.Lineup;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 흥미 순간 이벤트(game_events) 추출. 라이브 계산 중 임계 통과분을 append하며,
 * (game_id, event_type, source_type, source_ref) UNIQUE로 재관측 시 중복을 막는다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class GameEventExtractor {

    static final String EVENT_PRESSURE_BASES_LOADED = "pressure_bases_loaded";
    static final String EVENT_PRESSURE_SCORING_POSITION = "pressure_scoring_position";
    static final String EVENT_LONG_AT_BAT = "long_at_bat";
    static final String EVENT_FULL_COUNT_TWO_OUT = "full_count_two_out";
    static final String EVENT_PITCHER_INSTABILITY = "pitcher_instability";
    static final String EVENT_HARD_CONTACT = "hard_contact";
    static final String EVENT_SCORING_PLAY = "scoring_play";
    static final String EVENT_LEAD_CHANGE = "lead_change";
    static final String EVENT_HOME_RUN = "home_run";
    static final String EVENT_BIG_INNING = "big_inning";

    private final GameEventRepository gameEventRepository;
    private final LineupRepository lineupRepository;
    private final PlayRepository playRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ScoringProperties props;

    public void extract(
            long gameId,
            List<Play> recentPlays,
            List<PlateAppearanceSnapshot> plateAppearances,
            int seedLeader,
            Instant observedAt
    ) {
        extractPlayEvents(gameId, recentPlays == null ? List.of() : recentPlays, seedLeader, observedAt);
        extractPlateAppearanceEvents(
                gameId,
                plateAppearances == null ? List.of() : plateAppearances,
                observedAt
        );
    }

    private void extractPlayEvents(long gameId, List<Play> recentPlays, int seedLeader, Instant observedAt) {
        int lastLeader = seedLeader;
        Set<HalfInning> scoringHalves = new HashSet<>();

        for (List<Play> atBat : consecutiveAtBats(recentPlays)) {
            Play first = atBat.get(0);
            boolean basesLoaded = Boolean.TRUE.equals(first.getRunnerOnFirst())
                    && Boolean.TRUE.equals(first.getRunnerOnSecond())
                    && Boolean.TRUE.equals(first.getRunnerOnThird());
            boolean scoringPosition = Boolean.TRUE.equals(first.getRunnerOnSecond())
                    || Boolean.TRUE.equals(first.getRunnerOnThird());
            boolean twoOutsAtStart = valueOrZero(first.getOuts()) >= 2;

            if (basesLoaded) {
                appendPlayIfAbsent(
                        gameId,
                        EVENT_PRESSURE_BASES_LOADED,
                        GameEvent.SPOILER_PROTECTED_SAFE,
                        first,
                        observedAt,
                        countPayload(first)
                );
            } else if (scoringPosition && twoOutsAtStart) {
                appendPlayIfAbsent(
                        gameId,
                        EVENT_PRESSURE_SCORING_POSITION,
                        GameEvent.SPOILER_PROTECTED_SAFE,
                        first,
                        observedAt,
                        countPayload(first)
                );
            }

            atBat.stream()
                    .filter(play -> valueOrZero(play.getOuts()) >= 2)
                    .filter(play -> valueOrZero(play.getBalls()) >= 3 && valueOrZero(play.getStrikes()) >= 2)
                    .findFirst()
                    .ifPresent(play -> appendPlayIfAbsent(
                            gameId,
                            EVENT_FULL_COUNT_TWO_OUT,
                            GameEvent.SPOILER_PROTECTED_SAFE,
                            play,
                            observedAt,
                            countPayload(play)
                    ));
        }

        for (Play play : recentPlays) {
            if (play.getPlayOrder() == null) {
                continue;
            }

            if (Boolean.TRUE.equals(play.getScoringPlay())) {
                appendPlayIfAbsent(
                        gameId,
                        EVENT_SCORING_PLAY,
                        GameEvent.SPOILER_REVEALED_ONLY,
                        play,
                        observedAt,
                        scorePayload(play)
                );
                scoringHalves.add(new HalfInning(play.getInning(), play.getInningType()));
            }
            if (isHomeRun(play)) {
                appendPlayIfAbsent(
                        gameId,
                        EVENT_HOME_RUN,
                        GameEvent.SPOILER_REVEALED_ONLY,
                        play,
                        observedAt,
                        scorePayload(play)
                );
            }

            Integer leader = leaderOf(play);
            if (leader != null && leader != 0) {
                if (lastLeader != 0 && leader != lastLeader) {
                    appendPlayIfAbsent(
                            gameId,
                            EVENT_LEAD_CHANGE,
                            GameEvent.SPOILER_REVEALED_ONLY,
                            play,
                            observedAt,
                            Map.of()
                    );
                }
                lastLeader = leader;
            }
        }

        scoringHalves.forEach(half -> {
                    long scoringPlayCount = playRepository.countByGameIdAndInningAndInningTypeAndScoringPlayTrue(
                            gameId, half.inning(), half.inningType());
                    if (scoringPlayCount < props.bigInning().minScoringPlays()) {
                        return;
                    }
                    Play source = playRepository
                            .findFirstByGameIdAndInningAndInningTypeAndScoringPlayTrueOrderByPlayOrderAsc(
                                    gameId, half.inning(), half.inningType())
                            .orElse(null);
                    if (source == null) {
                        return;
                    }
                    appendPlayIfAbsent(
                            gameId,
                            EVENT_BIG_INNING,
                            GameEvent.SPOILER_REVEALED_ONLY,
                            source,
                            observedAt,
                            Map.of("scoringPlays", scoringPlayCount)
                    );
                });
    }

    private static List<List<Play>> consecutiveAtBats(List<Play> plays) {
        List<List<Play>> groups = new ArrayList<>();
        List<Play> current = new ArrayList<>();
        AtBatKey currentKey = null;

        for (Play play : plays) {
            if (play.getPlayOrder() == null || play.getBatterId() == null) {
                continue;
            }
            AtBatKey key = new AtBatKey(play.getInning(), play.getInningType(), play.getBatterId());
            if (currentKey != null && !currentKey.equals(key)) {
                groups.add(List.copyOf(current));
                current.clear();
            }
            current.add(play);
            currentKey = key;
        }
        if (!current.isEmpty()) {
            groups.add(List.copyOf(current));
        }
        return groups;
    }

    private void extractPlateAppearanceEvents(
            long gameId,
            List<PlateAppearanceSnapshot> plateAppearances,
            Instant observedAt
    ) {
        if (plateAppearances.isEmpty()) {
            return;
        }

        Set<Long> probablePitcherIds = lineupRepository.findByGameIdAndIsProbablePitcherTrue(gameId).stream()
                .map(Lineup::getPlayerId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        Map<Long, Deque<Double>> recentVelocityByPitcher = new HashMap<>();
        Set<Long> pitchCountThresholdReached = new HashSet<>();

        plateAppearances.stream()
                .sorted(Comparator.comparingLong(PlateAppearanceSnapshot::paNumber))
                .forEach(plateAppearance -> {
                    extractLongAtBat(gameId, plateAppearance, observedAt);
                    extractHardContact(gameId, plateAppearance, observedAt);
                    extractPitcherInstability(
                            gameId,
                            plateAppearance,
                            probablePitcherIds,
                            recentVelocityByPitcher,
                            pitchCountThresholdReached,
                            observedAt
                    );
                });
    }

    private void extractLongAtBat(long gameId, PlateAppearanceSnapshot plateAppearance, Instant observedAt) {
        int pitchCount = plateAppearance.pitches().stream()
                .map(PitchSnapshot::pitchNumber)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(plateAppearance.pitches().size());
        if (pitchCount < props.detail().longAtBatPitches()) {
            return;
        }
        appendPlateAppearanceIfAbsent(
                gameId,
                EVENT_LONG_AT_BAT,
                plateAppearance,
                observedAt,
                Map.of("pitchNumber", pitchCount)
        );
    }

    private void extractHardContact(long gameId, PlateAppearanceSnapshot plateAppearance, Instant observedAt) {
        double maxExitVelocity = plateAppearance.pitches().stream()
                .map(PitchSnapshot::exitVelocity)
                .filter(java.util.Objects::nonNull)
                .max(Double::compareTo)
                .orElse(0.0);
        boolean barrel = plateAppearance.pitches().stream().anyMatch(PitchSnapshot::barrel);
        if (!barrel && maxExitVelocity < props.detail().hardContactExitVelocity()) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("isBarrel", barrel);
        if (maxExitVelocity > 0) {
            payload.put("exitVelocity", maxExitVelocity);
        }
        if (plateAppearance.outs() != null) {
            payload.put("outs", plateAppearance.outs());
        }
        payload.put("runnerOnFirst", plateAppearance.runnerOnFirst());
        payload.put("runnerOnSecond", plateAppearance.runnerOnSecond());
        payload.put("runnerOnThird", plateAppearance.runnerOnThird());
        appendPlateAppearanceIfAbsent(
                gameId,
                EVENT_HARD_CONTACT,
                plateAppearance,
                observedAt,
                payload
        );
    }

    private void extractPitcherInstability(
            long gameId,
            PlateAppearanceSnapshot plateAppearance,
            Set<Long> probablePitcherIds,
            Map<Long, Deque<Double>> recentVelocityByPitcher,
            Set<Long> pitchCountThresholdReached,
            Instant observedAt
    ) {
        Long pitcherId = plateAppearance.pitcherId();
        if (pitcherId == null) {
            return;
        }

        boolean starterPitchCountReached = false;
        Integer maxPitchCount = null;
        Double maxVelocityDrop = null;
        Deque<Double> velocityWindow = recentVelocityByPitcher.computeIfAbsent(
                pitcherId,
                ignored -> new ArrayDeque<>()
        );

        for (PitchSnapshot pitch : plateAppearance.pitches()) {
            if (pitch.pitcherPitchCount() != null) {
                maxPitchCount = maxPitchCount == null
                        ? pitch.pitcherPitchCount()
                        : Math.max(maxPitchCount, pitch.pitcherPitchCount());
                if (probablePitcherIds.contains(pitcherId)
                        && !pitchCountThresholdReached.contains(pitcherId)
                        && pitch.pitcherPitchCount() >= props.detail().starterPitchCount()) {
                    starterPitchCountReached = true;
                    pitchCountThresholdReached.add(pitcherId);
                }
            }

            if (pitch.releaseSpeed() == null) {
                continue;
            }
            if (velocityWindow.size() >= props.detail().velocityDropWindowPitches()) {
                double baseline = velocityWindow.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double drop = baseline - pitch.releaseSpeed();
                if (drop >= props.detail().velocityDropMph()) {
                    maxVelocityDrop = maxVelocityDrop == null ? drop : Math.max(maxVelocityDrop, drop);
                }
            }
            velocityWindow.addLast(pitch.releaseSpeed());
            while (velocityWindow.size() > props.detail().velocityDropWindowPitches()) {
                velocityWindow.removeFirst();
            }
        }

        if (!starterPitchCountReached && maxVelocityDrop == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (maxPitchCount != null) {
            payload.put("pitcherPitchCount", maxPitchCount);
        }
        if (maxVelocityDrop != null) {
            payload.put("velocityDropMph", roundOneDecimal(maxVelocityDrop));
        }
        appendPlateAppearanceIfAbsent(
                gameId,
                EVENT_PITCHER_INSTABILITY,
                plateAppearance,
                observedAt,
                payload
        );
    }

    private void appendPlayIfAbsent(
            long gameId,
            String eventType,
            String spoilerLevel,
            Play play,
            Instant observedAt,
            Map<String, Object> payload
    ) {
        appendIfAbsent(
                gameId,
                eventType,
                spoilerLevel,
                GameEvent.SOURCE_TYPE_PLAY,
                play.getPlayOrder(),
                play.getInning(),
                play.getInningType(),
                play.getBatterId(),
                play.getPitcherId(),
                observedAt,
                payload
        );
    }

    private void appendPlateAppearanceIfAbsent(
            long gameId,
            String eventType,
            PlateAppearanceSnapshot plateAppearance,
            Instant observedAt,
            Map<String, Object> payload
    ) {
        appendIfAbsent(
                gameId,
                eventType,
                GameEvent.SPOILER_PROTECTED_SAFE,
                GameEvent.SOURCE_TYPE_PA,
                plateAppearance.paNumber(),
                plateAppearance.inning(),
                plateAppearance.inningType(),
                plateAppearance.batterId(),
                plateAppearance.pitcherId(),
                observedAt,
                payload
        );
    }

    private void appendIfAbsent(
            long gameId,
            String eventType,
            String spoilerLevel,
            String sourceType,
            Long sourceRef,
            Integer inning,
            String inningType,
            Long batterId,
            Long pitcherId,
            Instant observedAt,
            Map<String, Object> payload
    ) {
        if (sourceRef == null
                || gameEventRepository.existsByGameIdAndEventTypeAndSourceTypeAndSourceRef(
                gameId, eventType, sourceType, sourceRef)) {
            return;
        }
        if (GameEvent.SPOILER_PROTECTED_SAFE.equals(spoilerLevel)
                && gameEventRepository.countByGameIdAndEventType(gameId, eventType)
                >= props.detail().maxEventsPerTypePerGame()) {
            return;
        }

        GameEvent event = new GameEvent();
        event.setGameId(gameId);
        event.setEventType(eventType);
        event.setSpoilerLevel(spoilerLevel);
        event.setSourceType(sourceType);
        event.setSourceRef(sourceRef);
        event.setInning(inning);
        event.setInningType(inningType);
        event.setBatterId(batterId);
        event.setPitcherId(pitcherId);
        event.setPayload(payload == null || payload.isEmpty() ? null : payload);
        event.setRulesetVersion(String.valueOf(props.version()));
        event.setObservedAt(observedAt);
        event.setBackfilled(false);
        event.setSource("OPERATIONAL");

        GameEvent saved = gameEventRepository.save(event);
        publishEventCopyRequest(saved, observedAt);
    }

    private void publishEventCopyRequest(GameEvent event, Instant observedAt) {
        if (!GameEvent.SPOILER_PROTECTED_SAFE.equals(event.getSpoilerLevel())) {
            return;
        }
        // 하이라이트 트리거가 켜지면 문구 생성은 급변 anchor 단위로만 하므로,
        // 이벤트별 문구 생성 트리거는 건너뛴다.
        ScoringProperties.Highlight highlight = props.highlight();
        if (highlight != null && highlight.enabled()) {
            return;
        }
        applicationEventPublisher.publishEvent(new GameEventCopyRequestedEvent(
                event.getGameId(), event.getId(), AiGenerationTrigger.MODE_PROTECTED, observedAt));
    }

    private static Map<String, Object> scorePayload(Play play) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (play.getScoreValue() != null) {
            payload.put("scoreValue", play.getScoreValue());
        }
        return payload;
    }

    private static Map<String, Object> countPayload(Play play) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (play.getOuts() != null) {
            payload.put("outs", play.getOuts());
        }
        if (play.getBalls() != null) {
            payload.put("balls", play.getBalls());
        }
        if (play.getStrikes() != null) {
            payload.put("strikes", play.getStrikes());
        }
        payload.put("runnerOnFirst", Boolean.TRUE.equals(play.getRunnerOnFirst()));
        payload.put("runnerOnSecond", Boolean.TRUE.equals(play.getRunnerOnSecond()));
        payload.put("runnerOnThird", Boolean.TRUE.equals(play.getRunnerOnThird()));
        return payload;
    }

    private static boolean isHomeRun(Play play) {
        String type = normalize(play.getType());
        String text = normalize(play.getText());
        return type.contains("home_run")
                || type.contains("home run")
                || text.contains("home run")
                || text.contains("homered");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static Integer leaderOf(Play play) {
        if (play.getHomeScore() == null || play.getAwayScore() == null) {
            return null;
        }
        return Integer.signum(play.getHomeScore() - play.getAwayScore());
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record HalfInning(Integer inning, String inningType) {
    }

    private record AtBatKey(Integer inning, String inningType, Long batterId) {
    }
}
