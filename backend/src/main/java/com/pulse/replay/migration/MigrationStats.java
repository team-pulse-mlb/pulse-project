package com.pulse.replay.migration;

import java.util.LinkedHashMap;
import java.util.Map;

class MigrationStats {

    private final Map<String, Counts> tableCounts = new LinkedHashMap<>();
    private final Map<String, Integer> prefixObjectCounts = new LinkedHashMap<>();

    void inserted(String table) {
        counts(table).inserted++;
    }

    void updated(String table) {
        counts(table).updated++;
    }

    void updated(String table, int amount) {
        counts(table).updated += amount;
    }

    void skipped(String table) {
        counts(table).skipped++;
    }

    void skipped(String table, int amount) {
        counts(table).skipped += amount;
    }

    void prefixObjects(String prefix, int amount) {
        prefixObjectCounts.put(prefix, amount);
    }

    Map<String, Counts> tableCounts() {
        return tableCounts;
    }

    Map<String, Integer> prefixObjectCounts() {
        return prefixObjectCounts;
    }

    private Counts counts(String table) {
        return tableCounts.computeIfAbsent(table, ignored -> new Counts());
    }

    static class Counts {
        int inserted;
        int updated;
        int skipped;
    }
}
