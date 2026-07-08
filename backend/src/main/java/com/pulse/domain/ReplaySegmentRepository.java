package com.pulse.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplaySegmentRepository extends JpaRepository<ReplaySegment, Long> {

    Optional<ReplaySegment> findFirstByGameIdAndStatusOrderByOpenedAtDesc(Long gameId, String status);

    Optional<ReplaySegment> findTopByGameIdAndStatusOrderByClosedAtDesc(Long gameId, String status);

    long countByGameId(Long gameId);
}
