package com.pulse.domain;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StandingRepository extends JpaRepository<Standing, Long> {

    Optional<Standing> findBySeasonAndSnapshotDateAndTeamId(Integer season, LocalDate snapshotDate, Long teamId);

    Optional<Standing> findTopByTeamIdOrderBySnapshotDateDesc(Long teamId);
}
