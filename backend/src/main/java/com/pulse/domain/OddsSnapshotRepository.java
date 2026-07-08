package com.pulse.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OddsSnapshotRepository extends JpaRepository<OddsSnapshot, Long> {

    Optional<OddsSnapshot> findByGameIdAndVendorAndSnapshotType(Long gameId, String vendor, String snapshotType);

    List<OddsSnapshot> findByGameIdAndSnapshotType(Long gameId, String snapshotType);
}
