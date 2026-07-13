package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class TensionCurveQueryServiceTest {

    private final WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
    private final TensionCurveQueryService service = new TensionCurveQueryService(
            watchScoreRepository,
            TestScoringProperties.version5()
    );

    @Test
    void protectedCurve_usesMaximumScorePerInningAndHidesInningType() {
        when(watchScoreRepository.findByGameIdOrderByComputedAtAsc(10L)).thenReturn(List.of(
                score(1, "Top", 10),
                score(1, "Bottom", 41),
                score(2, "Top", 80),
                score(2, "Bottom", 81)
        ));

        assertThat(service.getProtectedCurve(10L)).containsExactly(
                new TensionCurveQueryService.ProtectedPoint(1, 3),
                new TensionCurveQueryService.ProtectedPoint(2, 5)
        );
    }

    @Test
    void revealedCurve_usesMaximumScorePerHalfInningAndSupportsExtraInnings() {
        when(watchScoreRepository.findByGameIdOrderByComputedAtAsc(20L)).thenReturn(List.of(
                score(9, "top", 20),
                score(9, "Top", 21),
                score(9, "Bottom", 60),
                score(10, "Top", 100)
        ));

        assertThat(service.getRevealedCurve(20L)).containsExactly(
                new TensionCurveQueryService.RevealedPoint(9, "Top", 2),
                new TensionCurveQueryService.RevealedPoint(9, "Bottom", 3),
                new TensionCurveQueryService.RevealedPoint(10, "Top", 5)
        );
    }

    @Test
    void curves_skipIncompleteHistoryAndClampOutOfRangeScores() {
        when(watchScoreRepository.findByGameIdOrderByComputedAtAsc(30L)).thenReturn(List.of(
                score(null, "Top", 50),
                score(3, null, 70),
                score(4, "Mid", 90),
                score(5, "Bottom", -1),
                score(6, "Top", 101),
                score(7, "Top", null)
        ));

        assertThat(service.getProtectedCurve(30L)).containsExactly(
                new TensionCurveQueryService.ProtectedPoint(3, 4),
                new TensionCurveQueryService.ProtectedPoint(4, 5),
                new TensionCurveQueryService.ProtectedPoint(5, 1),
                new TensionCurveQueryService.ProtectedPoint(6, 5)
        );
        assertThat(service.getRevealedCurve(30L)).containsExactly(
                new TensionCurveQueryService.RevealedPoint(5, "Bottom", 1),
                new TensionCurveQueryService.RevealedPoint(6, "Top", 5)
        );
    }

    private static WatchScore score(Integer inning, String inningType, Integer baseScore) {
        WatchScore score = new WatchScore();
        score.setInning(inning);
        score.setInningType(inningType);
        score.setBaseScore(baseScore);
        return score;
    }
}
