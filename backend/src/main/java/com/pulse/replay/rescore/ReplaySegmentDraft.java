package com.pulse.replay.rescore;

import com.pulse.domain.ReplaySegment;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

class ReplaySegmentDraft {

    private final Long gameId;
    private Long startPlayOrder;
    private Long endPlayOrder;
    private Integer startInning;
    private Integer endInning;
    private String startInningType;
    private String endInningType;
    private int peakScore;
    private final LinkedHashSet<String> tags = new LinkedHashSet<>();
    private String status;
    private Instant openedAt;
    private Instant closedAt;
    private String source;

    private ReplaySegmentDraft(Long gameId) {
        this.gameId = gameId;
        this.status = ReplaySegment.STATUS_OPEN;
    }

    static ReplaySegmentDraft open(Long gameId, RescoreScorePoint point) {
        ReplaySegmentDraft draft = new ReplaySegmentDraft(gameId);
        draft.startPlayOrder = point.playOrder();
        draft.endPlayOrder = point.playOrder();
        draft.startInning = point.inning();
        draft.endInning = point.inning();
        draft.startInningType = point.inningType();
        draft.endInningType = point.inningType();
        draft.openedAt = point.computedAt();
        draft.source = sourceValue(point);
        return draft;
    }

    void reopen() {
        status = ReplaySegment.STATUS_OPEN;
        closedAt = null;
    }

    void applyPeak(RescoreScorePoint point) {
        endPlayOrder = point.playOrder();
        endInning = point.inning();
        endInningType = point.inningType();
        peakScore = Math.max(peakScore, point.baseScore());
        if (point.tags() != null) {
            tags.addAll(point.tags());
        }
        source = sourceValue(point);
    }

    void closeAt(RescoreScorePoint point) {
        endPlayOrder = point.playOrder();
        endInning = point.inning();
        endInningType = point.inningType();
        status = ReplaySegment.STATUS_CLOSED;
        closedAt = point.computedAt();
        source = sourceValue(point);
    }

    boolean isClosed() {
        return ReplaySegment.STATUS_CLOSED.equals(status);
    }

    Long gameId() {
        return gameId;
    }

    Long startPlayOrder() {
        return startPlayOrder;
    }

    Long endPlayOrder() {
        return endPlayOrder;
    }

    Integer startInning() {
        return startInning;
    }

    Integer endInning() {
        return endInning;
    }

    String startInningType() {
        return startInningType;
    }

    String endInningType() {
        return endInningType;
    }

    int peakScore() {
        return peakScore;
    }

    List<String> tags() {
        return List.copyOf(tags);
    }

    String status() {
        return status;
    }

    Instant openedAt() {
        return openedAt;
    }

    Instant closedAt() {
        return closedAt;
    }

    String source() {
        return source == null ? "OPERATIONAL" : source;
    }

    private static String sourceValue(RescoreScorePoint point) {
        return point.source() == null ? "OPERATIONAL" : point.source();
    }
}
