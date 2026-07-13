package com.pulse.poller;

import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.domain.Play;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PollerRunnerStateMatcher {

    public MatchResult match(List<Play> plays, List<BdlPlateAppearance> plateAppearances) {
        List<PlayGroup> groups = buildGroups(plays);
        Map<HalfKey, List<BdlPlateAppearance>> plateAppearancesByHalf = groupPlateAppearances(plateAppearances);
        List<RunnerStateUpdate> updates = new ArrayList<>();
        int unmatchedGroups = 0;
        int matchedGroups = 0;

        for (PlayGroup group : groups) {
            List<BdlPlateAppearance> candidates = plateAppearancesByHalf.getOrDefault(group.halfKey(), List.of());
            BdlPlateAppearance plateAppearance = kthByBatter(candidates, group.batterId(), group.batterIndex());
            if (plateAppearance == null) {
                unmatchedGroups++;
                continue;
            }
            matchedGroups++;
            for (Play play : group.plays()) {
                updates.add(new RunnerStateUpdate(
                        play.getPlayOrder(),
                        plateAppearance.runnerOnFirst(),
                        plateAppearance.runnerOnSecond(),
                        plateAppearance.runnerOnThird()
                ));
            }
        }

        int unmatchedPlateAppearances = Math.max(0, plateAppearances.size() - matchedGroups);
        return new MatchResult(updates, unmatchedPlateAppearances, unmatchedGroups);
    }

    private List<PlayGroup> buildGroups(List<Play> plays) {
        List<Play> sorted = plays.stream()
                .sorted(Comparator.comparing(Play::getPlayOrder))
                .toList();
        List<PlayGroup> groups = new ArrayList<>();
        List<Play> current = new ArrayList<>();
        HalfKey currentHalf = null;
        Long currentBatter = null;
        Map<BatterKey, Integer> batterCounts = new HashMap<>();

        for (Play play : sorted) {
            String half = normalizeHalf(play.getInningType());
            if (play.getBatterId() == null || half == null) {
                continue;
            }

            HalfKey halfKey = new HalfKey(play.getInning(), half);
            if (currentBatter == null || currentBatter.equals(play.getBatterId()) && halfKey.equals(currentHalf)) {
                current.add(play);
                currentHalf = halfKey;
                currentBatter = play.getBatterId();
                continue;
            }

            groups.add(toGroup(currentHalf, currentBatter, current, batterCounts));
            current = new ArrayList<>();
            current.add(play);
            currentHalf = halfKey;
            currentBatter = play.getBatterId();
        }

        if (!current.isEmpty() && currentBatter != null) {
            groups.add(toGroup(currentHalf, currentBatter, current, batterCounts));
        }
        return groups;
    }

    private PlayGroup toGroup(HalfKey halfKey, Long batterId, List<Play> plays,
                              Map<BatterKey, Integer> batterCounts) {
        BatterKey batterKey = new BatterKey(halfKey, batterId);
        int index = batterCounts.merge(batterKey, 1, Integer::sum);
        return new PlayGroup(halfKey, batterId, index, List.copyOf(plays));
    }

    private Map<HalfKey, List<BdlPlateAppearance>> groupPlateAppearances(
            List<BdlPlateAppearance> plateAppearances
    ) {
        Map<HalfKey, List<BdlPlateAppearance>> byHalf = new HashMap<>();
        for (BdlPlateAppearance plateAppearance : plateAppearances.stream()
                .sorted(Comparator.comparing(BdlPlateAppearance::paNumber))
                .toList()) {
            String half = normalizeHalf(plateAppearance.halfInning());
            if (half == null) {
                continue;
            }
            byHalf.computeIfAbsent(
                    new HalfKey(plateAppearance.inning(), half),
                    ignored -> new ArrayList<>()
            ).add(plateAppearance);
        }
        return byHalf;
    }

    private BdlPlateAppearance kthByBatter(
            List<BdlPlateAppearance> plateAppearances,
            Long batterId,
            int index
    ) {
        int seen = 0;
        for (BdlPlateAppearance plateAppearance : plateAppearances) {
            if (batterId.equals(plateAppearance.batterId())) {
                seen++;
                if (seen == index) {
                    return plateAppearance;
                }
            }
        }
        return null;
    }

    private static String normalizeHalf(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("top".equals(normalized) || "bottom".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    public record MatchResult(List<RunnerStateUpdate> updates, int unmatchedPlateAppearances, int unmatchedGroups) {
    }

    public record RunnerStateUpdate(
            Long playOrder,
            Boolean runnerOnFirst,
            Boolean runnerOnSecond,
            Boolean runnerOnThird
    ) {
    }

    private record HalfKey(Integer inning, String half) {
    }

    private record BatterKey(HalfKey halfKey, Long batterId) {
    }

    private record PlayGroup(HalfKey halfKey, Long batterId, int batterIndex, List<Play> plays) {
    }
}
