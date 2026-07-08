package com.pulse.replay.rescore;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.domain.ReplaySegment;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RescoreSegmentBuilderTest {

    private final RescoreSegmentBuilder builder = new RescoreSegmentBuilder(60);
    private final Instant baseTime = Instant.parse("2026-07-02T03:00:00Z");

    @Test
    void closesOpenSegmentAtEndOfReplay() {
        List<ReplaySegmentDraft> segments = builder.build(1L, List.of(
                point(1, "Top", 70, 100L, "후반 긴장 구간"),
                point(1, "Top", 65, 101L, "접전 흐름")
        ));

        assertThat(segments).hasSize(1);
        ReplaySegmentDraft segment = segments.get(0);
        assertThat(segment.status()).isEqualTo(ReplaySegment.STATUS_CLOSED);
        assertThat(segment.startPlayOrder()).isEqualTo(100L);
        assertThat(segment.endPlayOrder()).isEqualTo(101L);
        assertThat(segment.peakScore()).isEqualTo(70);
        assertThat(segment.tags()).containsExactly("후반 긴장 구간", "접전 흐름");
        assertThat(segment.closedAt()).isEqualTo(baseTime.plusSeconds(101));
    }

    @Test
    void mergesNearPreviousClosedSegmentWithinOneHalfInning() {
        List<ReplaySegmentDraft> segments = builder.build(1L, List.of(
                point(7, "Top", 70, 100L, "후반 긴장 구간"),
                point(7, "Top", 40, 101L, "닫힘"),
                point(7, "Bottom", 75, 102L, "접전 흐름")
        ));

        assertThat(segments).hasSize(1);
        ReplaySegmentDraft segment = segments.get(0);
        assertThat(segment.status()).isEqualTo(ReplaySegment.STATUS_CLOSED);
        assertThat(segment.startInning()).isEqualTo(7);
        assertThat(segment.startInningType()).isEqualTo("Top");
        assertThat(segment.endInning()).isEqualTo(7);
        assertThat(segment.endInningType()).isEqualTo("Bottom");
        assertThat(segment.peakScore()).isEqualTo(75);
    }

    private RescoreScorePoint point(int inning, String inningType, int score, long playOrder, String tag) {
        return new RescoreScorePoint(
                1L,
                baseTime.plusSeconds(playOrder),
                playOrder,
                inning,
                inningType,
                score,
                List.of(tag),
                "S3_LIVE_ARCHIVE");
    }
}
