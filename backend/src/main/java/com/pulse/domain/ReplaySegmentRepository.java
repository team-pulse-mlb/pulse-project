package com.pulse.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplaySegmentRepository extends JpaRepository<ReplaySegment, Long> {

    Optional<ReplaySegment> findFirstByGameIdAndOpenSegmentTrueOrderByOpenedAtDesc(Long gameId);

    Optional<ReplaySegment> findTopByGameIdAndOpenSegmentFalseOrderByClosedAtDesc(Long gameId);

    long countByGameId(Long gameId);
}
