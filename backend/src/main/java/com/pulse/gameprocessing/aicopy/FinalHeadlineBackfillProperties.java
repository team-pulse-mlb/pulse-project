package com.pulse.gameprocessing.aicopy;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 종료 경기 헤드라인 백필 대상. 비어 있으면 누락된 전체 종료 경기를 조회합니다. */
@ConfigurationProperties(prefix = "pulse.headline-backfill")
public record FinalHeadlineBackfillProperties(List<Long> gameIds) {

    public FinalHeadlineBackfillProperties {
        gameIds = gameIds == null ? List.of() : gameIds.stream().distinct().toList();
    }
}
