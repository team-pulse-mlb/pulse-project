package com.pulse.replay.rescore;

import java.util.ArrayList;
import java.util.List;

class RescoreSegmentBuilder {

    private static final int CLOSE_SCORE = 50;

    private final int openScore;

    RescoreSegmentBuilder(int openScore) {
        this.openScore = openScore;
    }

    List<ReplaySegmentDraft> build(Long gameId, List<RescoreScorePoint> points) {
        List<ReplaySegmentDraft> segments = new ArrayList<>();
        ReplaySegmentDraft openSegment = null;
        RescoreScorePoint latestPoint = null;

        for (RescoreScorePoint point : points) {
            latestPoint = point;
            if (point.baseScore() >= openScore) {
                if (openSegment == null) {
                    openSegment = openOrMerge(gameId, segments, point);
                }
                openSegment.applyPeak(point);
                continue;
            }

            if (point.baseScore() < CLOSE_SCORE && openSegment != null) {
                openSegment.closeAt(point);
                openSegment = null;
            }
        }

        if (openSegment != null && latestPoint != null) {
            openSegment.closeAt(latestPoint);
        }

        return segments;
    }

    private ReplaySegmentDraft openOrMerge(Long gameId, List<ReplaySegmentDraft> segments, RescoreScorePoint point) {
        ReplaySegmentDraft previous = latestClosed(segments);
        if (previous != null && isNear(previous, point)) {
            previous.reopen();
            return previous;
        }

        ReplaySegmentDraft draft = ReplaySegmentDraft.open(gameId, point);
        segments.add(draft);
        return draft;
    }

    private ReplaySegmentDraft latestClosed(List<ReplaySegmentDraft> segments) {
        for (int i = segments.size() - 1; i >= 0; i--) {
            ReplaySegmentDraft segment = segments.get(i);
            if (segment.isClosed()) {
                return segment;
            }
        }
        return null;
    }

    private boolean isNear(ReplaySegmentDraft previous, RescoreScorePoint point) {
        if (previous.endInning() == null || point.inning() == null) {
            return false;
        }
        return Math.abs(halfInningIndex(previous.endInning(), previous.endInningType())
                - halfInningIndex(point.inning(), point.inningType())) <= 1;
    }

    private static int halfInningIndex(Integer inning, String inningType) {
        int base = Math.max(1, inning) * 2;
        if (inningType != null && inningType.equalsIgnoreCase("Top")) {
            return base - 1;
        }
        return base;
    }
}
