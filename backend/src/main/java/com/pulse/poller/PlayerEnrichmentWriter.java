package com.pulse.poller;

import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선수 마스터 응답으로 id-only 스텁 선수의 상세(이름·포지션·팀)를 채운다.
 * 기존 행만 갱신하고 신규 행은 만들지 않으며, null 필드로 기존 값을 덮어쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PlayerEnrichmentWriter {

    private final PlayerRepository playerRepository;

    /** 응답 dto를 기존 players 행에 반영하고 갱신 건수를 돌려준다. */
    @Transactional
    public int applyPlayerDetails(List<BdlPlayer> dtos, Instant observedAt) {
        int updated = 0;
        for (BdlPlayer dto : dtos) {
            if (dto.id() == null) {
                continue;
            }
            Player player = playerRepository.findById(dto.id()).orElse(null);
            if (player == null) {
                continue;
            }
            if (dto.fullName() != null) {
                player.setFullName(dto.fullName());
            }
            if (dto.firstName() != null) {
                player.setFirstName(dto.firstName());
            }
            if (dto.lastName() != null) {
                player.setLastName(dto.lastName());
            }
            if (dto.position() != null) {
                player.setPosition(dto.position());
            }
            if (dto.team() != null && dto.team().id() != null) {
                player.setTeamId(dto.team().id());
            }
            player.setUpdatedAt(observedAt);
            playerRepository.save(player);
            updated++;
        }
        return updated;
    }
}
