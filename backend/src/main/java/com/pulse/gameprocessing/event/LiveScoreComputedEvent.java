package com.pulse.gameprocessing.event;

import java.time.Instant;
import java.util.List;

/**
 * 라이브 점수 계산 한 사이클의 불변 결과 이벤트.
 * 커밋 후 후속 fan-out(이후 단계에서 리스너로 이동)이 소비한다.
 */
public record LiveScoreComputedEvent(
        long gameId,
        Instant computedAt,
        double watchScore,
        int watchScoreRounded,
        int baseScoreRounded,
        List<String> tags,
        List<String> previousTags,
        Integer inning,
        String inningType,
        Long scoredPlayOrder,
        Long translationThroughPlayOrder,
        String lifecycleState,
        int scoringVersion
) {

    public LiveScoreComputedEvent {
        tags = tags == null ? List.of() : List.copyOf(tags);
        previousTags = previousTags == null ? List.of() : List.copyOf(previousTags);
    }
}
