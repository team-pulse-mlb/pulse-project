package com.pulse.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayRepository extends JpaRepository<Play, Long> {

    List<Play> findByGameIdOrderByPlayOrderDesc(Long gameId, Pageable pageable);

    List<Play> findByGameIdOrderByPlayOrderAsc(Long gameId);

    Optional<Play> findFirstByGameIdAndInningAndInningTypeAndScoringPlayTrueOrderByPlayOrderAsc(
            Long gameId, Integer inning, String inningType);

    long countByGameIdAndInningAndInningTypeAndScoringPlayTrue(
            Long gameId, Integer inning, String inningType);

    boolean existsByGameIdAndPlayOrder(Long gameId, Long playOrder);
}
