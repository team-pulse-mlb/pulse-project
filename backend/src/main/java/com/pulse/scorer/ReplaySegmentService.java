package com.pulse.scorer;

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
        return replaySegmentRepository.findFirstByGameIdAndStatusOrderByOpenedAtDesc(gameId, ReplaySegment.STATUS_OPEN)
                .orElseGet(() -> reopenNearPrevious(gameId, latest)
                        .orElseGet(() -> newSegment(gameId, latest, now)));
    }

    private java.util.Optional<ReplaySegment> reopenNearPrevious(long gameId, Play latest) {
        if (latest == null || latest.getInning() == null) {
            return java.util.Optional.empty();
        }
        return replaySegmentRepository.findTopByGameIdAndStatusOrderByClosedAtDesc(gameId, ReplaySegment.STATUS_CLOSED)
                .filter(previous -> previous.getEndInning() != null)
                .filter(previous -> Math.abs(halfInningIndex(previous.getEndInning(), previous.getEndInningType())
                        - halfInningIndex(latest.getInning(), latest.getInningType())) <= 1)
                .map(previous -> {
                    previous.setStatus(ReplaySegment.STATUS_OPEN);
                    previous.setClosedAt(null);
                    return previous;
                });
    }

    private ReplaySegment newSegment(long gameId, Play latest, Instant now) {
        ReplaySegment segment = new ReplaySegment();
        segment.setGameId(gameId);
        segment.setOpenedAt(now);
        segment.setStatus(ReplaySegment.STATUS_OPEN);
        if (latest != null) {
            segment.setStartPlayOrder(latest.getPlayOrder());
            segment.setEndPlayOrder(latest.getPlayOrder());
            segment.setStartInning(latest.getInning());
            segment.setStartInningType(latest.getInningType());
            segment.setEndInning(latest.getInning());
            segment.setEndInningType(latest.getInningType());
            segment.setSource(latest.getSource() == null ? "OPERATIONAL" : latest.getSource());
        }
        return segment;
    }

    private void applyLatest(ReplaySegment segment, Play latest, double baseScore, List<String> tags) {
        int currentPeak = segment.getPeakScore() == null ? 0 : segment.getPeakScore();
        segment.setPeakScore(Math.max(currentPeak, (int) Math.round(baseScore)));
        segment.setTags(mergeTags(segment.getTags(), tags));
        if (latest != null) {
            segment.setEndPlayOrder(latest.getPlayOrder());
            segment.setEndInning(latest.getInning());
            segment.setEndInningType(latest.getInningType());
            segment.setSource(latest.getSource() == null ? "OPERATIONAL" : latest.getSource());
        }
    }

    private void closeOpenSegment(long gameId, Play latest, Instant now) {
        replaySegmentRepository.findFirstByGameIdAndStatusOrderByOpenedAtDesc(gameId, ReplaySegment.STATUS_OPEN)
                .ifPresent(segment -> {
                    if (latest != null) {
                        segment.setEndPlayOrder(latest.getPlayOrder());
                        segment.setEndInning(latest.getInning());
                        segment.setEndInningType(latest.getInningType());
                        segment.setSource(latest.getSource() == null ? "OPERATIONAL" : latest.getSource());
                    }
                    segment.setStatus(ReplaySegment.STATUS_CLOSED);
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
