package com.pulse.scorer;

import com.pulse.common.config.ScoringProperties;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 종료 경기 상세에 제공할 긴장 곡선을 점수 이력에서 산출한다.
 * 원 점수는 노출하지 않고 구간별 최대값을 1~5 단계로 변환한다.
 */
@Service
public class TensionCurveQueryService {

    private final WatchScoreRepository watchScoreRepository;
    private final List<Integer> levelBoundaries;

    public TensionCurveQueryService(
            WatchScoreRepository watchScoreRepository,
            ScoringProperties scoringProperties
    ) {
        this.watchScoreRepository = watchScoreRepository;
        this.levelBoundaries = scoringProperties.tensionCurve().levelBoundaries();
    }

    /** 보호 모드용 이닝 단위 곡선. 초·말 구분을 응답에 포함하지 않는다. */
    public List<ProtectedPoint> getProtectedCurve(long gameId) {
        Map<Integer, Integer> maximumByInning = new LinkedHashMap<>();
        for (WatchScore score : validScores(gameId)) {
            maximumByInning.merge(score.getInning(), clamp(score.getBaseScore()), Math::max);
        }
        return maximumByInning.entrySet().stream()
                .map(entry -> new ProtectedPoint(entry.getKey(), quantize(entry.getValue())))
                .toList();
    }

    /** 공개 모드용 하프이닝 단위 곡선. Top과 Bottom 이력만 사용한다. */
    public List<RevealedPoint> getRevealedCurve(long gameId) {
        Map<HalfInning, Integer> maximumByHalfInning = new LinkedHashMap<>();
        for (WatchScore score : validScores(gameId)) {
            String inningType = normalizeInningType(score.getInningType());
            if (inningType == null) {
                continue;
            }
            maximumByHalfInning.merge(
                    new HalfInning(score.getInning(), inningType),
                    clamp(score.getBaseScore()),
                    Math::max
            );
        }
        return maximumByHalfInning.entrySet().stream()
                .map(entry -> new RevealedPoint(
                        entry.getKey().inning(),
                        entry.getKey().inningType(),
                        quantize(entry.getValue())
                ))
                .toList();
    }

    private List<WatchScore> validScores(long gameId) {
        return watchScoreRepository.findByGameIdOrderByComputedAtAsc(gameId).stream()
                .filter(score -> score.getInning() != null && score.getInning() > 0)
                .filter(score -> score.getBaseScore() != null)
                .toList();
    }

    private int quantize(int score) {
        for (int index = 0; index < levelBoundaries.size(); index++) {
            if (score <= levelBoundaries.get(index)) {
                return index + 1;
            }
        }
        return 5;
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static String normalizeInningType(String inningType) {
        if (inningType == null) {
            return null;
        }
        return switch (inningType.trim().toLowerCase(Locale.ROOT)) {
            case "top" -> "Top";
            case "bottom" -> "Bottom";
            default -> null;
        };
    }

    public record ProtectedPoint(int inning, int level) {}

    public record RevealedPoint(int inning, String inningType, int level) {}

    private record HalfInning(int inning, String inningType) {}
}
