package com.pulse.poller;

import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 저장 직전 미등록 선수를 id-only 스텁으로 보장한다. 라인업 밖 선수(구원 투수·대타)나
 * 콜업 직후 선수가 plays·시즌 스탯에 등장할 때 FK 위반을 막는다. 이름 등 상세는
 * 이후 라인업 동기화나 보강 배치가 채운다.
 */
@Component
@RequiredArgsConstructor
public class PlayerStubWriter {

    private final PlayerRepository playerRepository;

    public void ensurePlayerExists(Long playerId, Instant observedAt) {
        if (playerId == null || playerRepository.existsById(playerId)) {
            return;
        }
        Player player = new Player();
        player.setId(playerId);
        player.setCreatedAt(observedAt);
        player.setUpdatedAt(observedAt);
        playerRepository.save(player);
    }
}
