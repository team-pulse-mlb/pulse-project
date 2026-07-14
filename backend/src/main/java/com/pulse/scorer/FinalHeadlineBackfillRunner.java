package com.pulse.scorer;

import com.pulse.domain.GameRepository;
import java.util.EnumMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 과거 종료 경기의 누락된 AI 헤드라인을 실제 생성 경로로 채우는 일회성 배치입니다. */
@Component
@Profile("headline-backfill")
@RequiredArgsConstructor
@Slf4j
class FinalHeadlineBackfillRunner implements ApplicationRunner {

    private final FinalHeadlineBackfillProperties properties;
    private final GameRepository gameRepository;
    private final AiFinalHeadlineGenerator generator;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Long> gameIds = properties.gameIds().isEmpty()
                    ? gameRepository.findFinalGameIdsMissingHeadlines()
                    : properties.gameIds();
            EnumMap<AiFinalHeadlineGenerator.GenerationStatus, Integer> counts =
                    new EnumMap<>(AiFinalHeadlineGenerator.GenerationStatus.class);

            log.info("AI 종료 헤드라인 백필 시작: 대상={}건", gameIds.size());
            for (Long gameId : gameIds) {
                AiFinalHeadlineGenerator.GenerationStatus status = generator.generateSynchronously(gameId);
                counts.merge(status, 1, Integer::sum);
                log.info("AI 종료 헤드라인 백필 처리: gameId={}, status={}", gameId, status);
            }

            int failures = counts.getOrDefault(AiFinalHeadlineGenerator.GenerationStatus.NOT_GENERATED, 0)
                    + counts.getOrDefault(AiFinalHeadlineGenerator.GenerationStatus.NOT_ELIGIBLE, 0)
                    + counts.getOrDefault(AiFinalHeadlineGenerator.GenerationStatus.PARTIALLY_SAVED, 0);
            log.info("AI 종료 헤드라인 백필 완료: 대상={}건, 저장={}건, 부분 저장={}건, 기존={}건, 실패={}건",
                    gameIds.size(),
                    counts.getOrDefault(AiFinalHeadlineGenerator.GenerationStatus.SAVED, 0),
                    counts.getOrDefault(AiFinalHeadlineGenerator.GenerationStatus.PARTIALLY_SAVED, 0),
                    counts.getOrDefault(AiFinalHeadlineGenerator.GenerationStatus.ALREADY_PRESENT, 0),
                    failures);
            if (failures > 0) {
                throw new IllegalStateException("AI 종료 헤드라인 백필 실패: " + failures + "건");
            }
        } finally {
            applicationContext.close();
        }
    }
}
