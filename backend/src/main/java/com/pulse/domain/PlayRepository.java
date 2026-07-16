package com.pulse.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayRepository extends JpaRepository<Play, Long> {

    List<Play> findByGameIdOrderByPlayOrderDesc(Long gameId, Pageable pageable);

    /**
     * 특정 타입의 경기 플레이를 최신순으로 개수 제한 없이 조회한다.
     */
    List<Play> findByGameIdAndTypeIgnoreCaseOrderByPlayOrderDesc(
            Long gameId,
            String type);

    List<Play> findByGameIdOrderByPlayOrderAsc(Long gameId);

    /**
     * scorer가 마지막으로 관측한 play 순서까지의 미번역 타석 결과를 최신순으로 조회한다.
     *
     * 최근 플레이 화면에 표시 가능한 행만 조회해 AI 호출 낭비를 막고,
     * Pageable로 한 번의 비동기 처리량을 제한한다.
     */
    @Query("""
            select play
            from Play play
            where play.gameId = :gameId
              and play.playOrder <= :lastPlayOrder
              and lower(trim(play.type)) = 'play result'
              and play.text is not null
              and trim(play.text) <> ''
              and play.textKo is null
              and play.textKoAttempts < :maxAttempts
              and play.batterId is not null
              and play.inning is not null
              and play.inning > 0
              and lower(trim(play.inningType)) in ('top', 'bottom')
            order by play.playOrder desc
            """)
    List<Play> findPendingPlayTranslations(
            @Param("gameId") Long gameId,
            @Param("lastPlayOrder") Long lastPlayOrder,
            @Param("maxAttempts") int maxAttempts,
            Pageable pageable);

    /**
     * 전체 재처리용: 번역·시도 횟수 유무와 관계없이 번역 표시 가능한 타석 결과를 모두 조회한다.
     */
    @Query("""
            select play
            from Play play
            where play.gameId = :gameId
              and lower(trim(play.type)) = 'play result'
              and play.text is not null
              and trim(play.text) <> ''
              and play.batterId is not null
              and play.inning is not null
              and play.inning > 0
              and lower(trim(play.inningType)) in ('top', 'bottom')
            order by play.playOrder asc
            """)
    List<Play> findPlayTranslationReprocessTargets(@Param("gameId") Long gameId);

    Optional<Play> findFirstByGameIdAndInningAndInningTypeAndScoringPlayTrueOrderByPlayOrderAsc(
            Long gameId, Integer inning, String inningType);

    long countByGameIdAndInningAndInningTypeAndScoringPlayTrue(
            Long gameId, Integer inning, String inningType);

    boolean existsByGameIdAndPlayOrder(Long gameId, Long playOrder);
}
