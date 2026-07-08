package com.pulse.replay.migration;

import com.pulse.replay.migration.MigrationRows.DbPlayRow;
import com.pulse.replay.migration.MigrationRows.PlateAppearanceRow;
import com.pulse.replay.migration.MigrationRows.RunnerUpdate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class PlateAppearancePlayMatcher {

    MatchResult match(List<DbPlayRow> plays, List<PlateAppearanceRow> plateAppearances) {
        List<PlayGroup> groups = buildGroups(plays);
        Map<HalfKey, List<PlateAppearanceRow>> paByHalf = groupPlateAppearances(plateAppearances);
        List<RunnerUpdate> updates = new ArrayList<>();
        int unmatchedGroups = 0;
        int matchedGroups = 0;

        for (PlayGroup group : groups) {
            List<PlateAppearanceRow> candidates = paByHalf.getOrDefault(group.halfKey(), List.of());
            PlateAppearanceRow pa = kthByBatter(candidates, group.batterId(), group.batterIndex());
            if (pa == null) {
                unmatchedGroups++;
                continue;
            }
            matchedGroups++;
            for (DbPlayRow play : group.plays()) {
                updates.add(new RunnerUpdate(
                        play.gameId(),
                        play.playOrder(),
                        pa.runnerOnFirst(),
                        pa.runnerOnSecond(),
                        pa.runnerOnThird()
                ));
            }
        }

        int unmatchedPlateAppearances = Math.max(0, plateAppearances.size() - matchedGroups);
        return new MatchResult(updates, unmatchedPlateAppearances, unmatchedGroups);
    }

    private List<PlayGroup> buildGroups(List<DbPlayRow> plays) {
        List<DbPlayRow> sorted = plays.stream()
                .sorted(Comparator.comparing(DbPlayRow::playOrder))
                .toList();
        List<PlayGroup> groups = new ArrayList<>();
        List<DbPlayRow> current = new ArrayList<>();
        HalfKey currentHalf = null;
        Long currentBatter = null;
        Map<BatterKey, Integer> batterCounts = new HashMap<>();

        for (DbPlayRow play : sorted) {
            String half = normalizeHalf(play.inningType());
            if (play.batterId() == null || half == null) {
                continue;
            }

            HalfKey halfKey = new HalfKey(play.inning(), half);
            if (currentBatter == null || currentBatter.equals(play.batterId()) && halfKey.equals(currentHalf)) {
                current.add(play);
                currentHalf = halfKey;
                currentBatter = play.batterId();
                continue;
            }

            groups.add(toGroup(currentHalf, currentBatter, current, batterCounts));
            current = new ArrayList<>();
            current.add(play);
            currentHalf = halfKey;
            currentBatter = play.batterId();
        }

        if (!current.isEmpty() && currentBatter != null) {
            groups.add(toGroup(currentHalf, currentBatter, current, batterCounts));
        }
        return groups;
    }

    private PlayGroup toGroup(HalfKey halfKey, Long batterId, List<DbPlayRow> plays,
                              Map<BatterKey, Integer> batterCounts) {
        BatterKey batterKey = new BatterKey(halfKey, batterId);
        int index = batterCounts.merge(batterKey, 1, Integer::sum);
        return new PlayGroup(halfKey, batterId, index, List.copyOf(plays));
    }

    private Map<HalfKey, List<PlateAppearanceRow>> groupPlateAppearances(List<PlateAppearanceRow> plateAppearances) {
        Map<HalfKey, List<PlateAppearanceRow>> byHalf = new HashMap<>();
        for (PlateAppearanceRow pa : plateAppearances.stream()
                .sorted(Comparator.comparing(PlateAppearanceRow::paNumber))
                .toList()) {
            String half = normalizeHalf(pa.halfInning());
            if (half == null) {
                continue;
            }
            byHalf.computeIfAbsent(new HalfKey(pa.inning(), half), ignored -> new ArrayList<>()).add(pa);
        }
        return byHalf;
    }

    private PlateAppearanceRow kthByBatter(List<PlateAppearanceRow> plateAppearances, Long batterId, int index) {
        int seen = 0;
        for (PlateAppearanceRow pa : plateAppearances) {
            if (batterId.equals(pa.batterId())) {
                seen++;
                if (seen == index) {
                    return pa;
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

    record MatchResult(List<RunnerUpdate> updates, int unmatchedPlateAppearances, int unmatchedGroups) {
    }

    private record HalfKey(Integer inning, String half) {
    }

    private record BatterKey(HalfKey halfKey, Long batterId) {
    }

    private record PlayGroup(HalfKey halfKey, Long batterId, int batterIndex, List<DbPlayRow> plays) {
    }
}
