package com.pulse.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByStatus(String status);

    List<Game> findByLifecycleState(String lifecycleState);

    List<Game> findByLifecycleStateIn(Collection<String> lifecycleStates);
}
