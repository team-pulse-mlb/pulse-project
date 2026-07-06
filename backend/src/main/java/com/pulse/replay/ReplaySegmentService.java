package com.pulse.replay;

import com.pulse.common.config.ScoringProperties;
import com.pulse.domain.Play;
import com.pulse.domain.ReplaySegment;
import com.pulse.domain.ReplaySegmentRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReplaySegmentService {

    private static final int CLOSE_SCORE = 50;

    private final ReplaySegmentRepository replaySegmentRepository;
    private final ScoringProperties scoringProperties;

    public void update(long gameId, List<Play> recentPlays, double baseScore, List<String> tags, Instant now) {
        Play latest = recentPlays.isEmpty() ? null : recentPlays.get(recentPlays.size() - 1);
        if (baseScore >= scoringProperties.thresholds().replaySegmentScore()) {
            ReplaySegment segment = openOrMergeSegment(gameId, latest, now);
            applyLatest(segment, latest, baseScore, tags);
            replaySegmentRepository.save(segment);
            return;
        }

        if (baseScore < CLOSE_SCORE) {
            closeOpenSegment(gameId, latest, now);
        }
    }

    public void closeOpenSegment(long gameId, Instant now) {
        closeOpenSegment(gameId, null, now);
    }

    private ReplaySegment openOrMergeSegment(long gameId, Play latest, Instant now) {
        return replaySegmentRepository.findFirstByGameIdAndOpenSegmentTrueOrderByOpenedAtDesc(gameId)
                .orElseGet(() -> reopenNearPrevious(gameId, latest)
                        .orElseGet(() -> newSegment(gameId, latest, now)));
    }

    private java.util.Optional<ReplaySegment> reopenNearPrevious(long gameId, Play latest) {
        if (latest == null || latest.getInning() == null) {
            return java.util.Optional.empty();
        }
        return replaySegmentRepository.findTopByGameIdAndOpenSegmentFalseOrderByClosedAtDesc(gameId)
                .filter(previous -> previous.getEndInning() != null)
                .filter(previous -> Math.abs(halfInningIndex(previous.getEndInning(), previous.getEndInningType())
                        - halfInningIndex(latest.getInning(), latest.getInningType())) <= 1)
                .map(previous -> {
                    previous.setOpenSegment(true);
                    previous.setClosedAt(null);
                    return previous;
                });
    }

    private ReplaySegment newSegment(long gameId, Play latest, Instant now) {
        ReplaySegment segment = new ReplaySegment();
        segment.setGameId(gameId);
        segment.setOpenedAt(now);
        segment.setOpenSegment(true);
        if (latest != null) {
            segment.setStartPlayOrder(latest.getPlayOrder());
            segment.setEndPlayOrder(latest.getPlayOrder());
            segment.setStartInning(latest.getInning());
            segment.setStartInningType(latest.getInningType());
            segment.setEndInning(latest.getInning());
            segment.setEndInningType(latest.getInningType());
        }
        return segment;
    }

    private void applyLatest(ReplaySegment segment, Play latest, double baseScore, List<String> tags) {
        segment.setPeakBaseScore(Math.max(segment.getPeakBaseScore(), baseScore));
        segment.setTags(mergeTags(segment.getTags(), tags));
        if (latest != null) {
            segment.setEndPlayOrder(latest.getPlayOrder());
            segment.setEndInning(latest.getInning());
            segment.setEndInningType(latest.getInningType());
        }
    }

    private void closeOpenSegment(long gameId, Play latest, Instant now) {
        replaySegmentRepository.findFirstByGameIdAndOpenSegmentTrueOrderByOpenedAtDesc(gameId)
                .ifPresent(segment -> {
                    if (latest != null) {
                        segment.setEndPlayOrder(latest.getPlayOrder());
                        segment.setEndInning(latest.getInning());
                        segment.setEndInningType(latest.getInningType());
                    }
                    segment.setOpenSegment(false);
                    segment.setClosedAt(now);
                    replaySegmentRepository.save(segment);
                });
    }

    private static List<String> mergeTags(List<String> existing, List<String> next) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (existing != null) {
            merged.addAll(existing);
        }
        if (next != null) {
            merged.addAll(next);
        }
        return List.copyOf(merged);
    }

    private static int halfInningIndex(Integer inning, String inningType) {
        int base = Math.max(1, inning) * 2;
        if (inningType != null && inningType.equalsIgnoreCase("Top")) {
            return base - 1;
        }
        return base;
    }
}
