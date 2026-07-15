package com.pulse.api.user;

import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.Team;
import com.pulse.domain.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 선수 이름 검색으로 받아온 외부 API 결과를
 * 로컬 players 테이블에 저장하는 Writer입니다.
 *
 * 기존 PlayerEnrichmentWriter는 이미 존재하는 스텁 선수만
 * 보강하는 역할이므로 수정하지 않고,
 * 관심 선수 검색용 저장 책임을 별도 클래스로 분리합니다.
 */
@Component
@RequiredArgsConstructor
public class PlayerSearchCacheWriter {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;

    /**
     * 외부 API에서 검색한 선수들을 players 테이블에 저장합니다.
     *
     * - 기존 선수: 이름·포지션·팀 정보 갱신
     * - 신규 선수: 새로운 Player 행 생성
     * - 이름이 없는 잘못된 응답: 저장하지 않음
     * - teams 테이블에 없는 팀 ID: FK 문제를 막기 위해 저장하지 않음
     *
     * @param playerDtos 외부 API 선수 검색 결과
     * @param observedAt 검색 결과를 받은 시각
     * @return 저장 또는 갱신된 Player 목록
     */
    @Transactional
    public List<Player> upsertSearchResults(
            List<BdlPlayer> playerDtos,
            Instant observedAt
    ) {
        if (playerDtos == null || playerDtos.isEmpty()) {
            return List.of();
        }

        /*
         * 같은 선수 ID가 응답에 중복되어 있더라도
         * 한 번만 처리하도록 ID 기준 Map으로 정리합니다.
         *
         * LinkedHashMap을 사용해서 외부 API 응답 순서를 유지합니다.
         */
        Map<Long, BdlPlayer> dtoByPlayerId =
                playerDtos.stream()
                        .filter(this::isSavablePlayer)
                        .collect(
                                Collectors.toMap(
                                        BdlPlayer::id,
                                        Function.identity(),
                                        (first, duplicate) -> first,
                                        LinkedHashMap::new
                                )
                        );

        if (dtoByPlayerId.isEmpty()) {
            return List.of();
        }

        /*
         * 기존 선수들을 한 번에 조회합니다.
         *
         * 선수마다 findById()를 실행하면 N번의 SELECT가 발생하므로
         * findAllById()로 한 번에 가져옵니다.
         */
        Map<Long, Player> existingPlayersById =
                playerRepository
                        .findAllById(dtoByPlayerId.keySet())
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Player::getId,
                                        Function.identity()
                                )
                        );

        /*
         * 검색 결과에 포함된 팀 ID를 모읍니다.
         */
        Set<Long> requestedTeamIds =
                dtoByPlayerId.values()
                        .stream()
                        .filter(dto -> dto.team() != null)
                        .map(dto -> dto.team().id())
                        .filter(teamId ->
                                teamId != null &&
                                        teamId > 0
                        )
                        .collect(Collectors.toSet());

        /*
         * 실제 teams 테이블에 존재하는 팀만 조회합니다.
         *
         * players.team_id가 teams를 참조하므로,
         * 존재하지 않는 외부 팀 ID를 바로 넣으면
         * 외래 키 오류가 발생할 수 있습니다.
         */
        Set<Long> knownTeamIds =
                teamRepository
                        .findAllById(requestedTeamIds)
                        .stream()
                        .map(Team::getTeamId)
                        .collect(Collectors.toSet());

        List<Player> playersToSave = new ArrayList<>();

        for (BdlPlayer dto : dtoByPlayerId.values()) {
            Player player = existingPlayersById.get(
                    dto.id()
            );

            /*
             * DB에 없는 검색 결과라면 새 Player를 만듭니다.
             */
            if (player == null) {
                player = new Player();
                player.setId(dto.id());
                player.setCreatedAt(observedAt);
            }

            /*
             * 외부 API의 null 또는 빈 값이
             * 기존 정상 값을 덮어쓰지 않도록 검사합니다.
             */
            if (hasText(dto.fullName())) {
                player.setFullName(
                        dto.fullName().trim()
                );
            }

            if (hasText(dto.firstName())) {
                player.setFirstName(
                        dto.firstName().trim()
                );
            }

            if (hasText(dto.lastName())) {
                player.setLastName(
                        dto.lastName().trim()
                );
            }

            if (hasText(dto.position())) {
                player.setPosition(
                        dto.position().trim()
                );
            }

            /*
             * teams 테이블에 실제로 존재하는 팀일 때만
             * 선수의 team_id를 갱신합니다.
             *
             * 알려지지 않은 팀이라고 해서 기존 teamId를 null로
             * 덮어쓰지는 않습니다.
             */
            if (dto.team() != null &&
                    knownTeamIds.contains(
                            dto.team().id()
                    )) {

                player.setTeamId(
                        dto.team().id()
                );
            }

            player.setUpdatedAt(observedAt);
            playersToSave.add(player);
        }

        return playerRepository.saveAll(
                playersToSave
        );
    }

    /**
     * 검색 캐시에 저장할 수 있는 정상 선수 응답인지 검사합니다.
     */
    private boolean isSavablePlayer(
            BdlPlayer player
    ) {
        return player != null &&
                player.id() != null &&
                player.id() > 0 &&
                hasText(player.fullName());
    }

    /**
     * 문자열이 null 또는 공백만 있는 값인지 검사합니다.
     */
    private boolean hasText(
            String value
    ) {
        return value != null &&
                !value.isBlank();
    }
}