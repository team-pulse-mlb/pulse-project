package com.pulse.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayRepository extends JpaRepository<Play, Long> {

    List<Play> findByGameIdOrderByPlayOrderDesc(Long gameId, Pageable pageable);

    List<Play> findByGameIdOrderByPlayOrderAsc(Long gameId);

    boolean existsByGameIdAndPlayOrder(Long gameId, Long playOrder);
}
