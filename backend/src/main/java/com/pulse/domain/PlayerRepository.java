package com.pulse.domain;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    /** 이름이 채워지지 않은 스텁 선수 조회. 보강 배치의 런당 상한 제한용으로 Pageable을 받는다. */
    List<Player> findByFullNameIsNull(Pageable pageable);
}
