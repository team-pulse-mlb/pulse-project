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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 사용자가 관심 선수로 등록한 선수 정보를
 * players 테이블에 저장하거나 갱신하는 Writer입니다.
 *
 * 선수 이름 검색 GET 요청에서는 이 Writer를 사용하지 않습니다.
 *
 * 사용자가 PUT /api/me/preferences로 관심 선수를 저장할 때만
 * 선택된 선수 정보를 players 테이블에 반영합니다.
 *
 * 기존 PlayerEnrichmentWriter는 경기 수집 과정에서 생성된
 * 이름 없는 스텁 선수를 보강하는 역할이므로 별도로 유지합니다.
 */
@Component
@RequiredArgsConstructor
public class PlayerRegistrationWriter {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;

    /**
     * 관심 선수 등록 대상으로 확인된 선수들을
     * players 테이블에 저장하거나 갱신합니다.
     *
     * - 기존 선수: 이름·포지션·팀 정보 갱신
     * - 신규 선수: 새로운 Player 행 생성
     * - 이름이 없는 잘못된 응답: 저장하지 않음
     * - 로컬 teams에 없는 팀 ID: team_id를 연결하지 않음
     * - 외부 null·빈 값: 기존 정상 값을 덮어쓰지 않음
     *
     * @param playerDtos 외부 API 또는 검색 캐시에서 확인한 선수 정보
     * @param observedAt 선수 정보를 확인한 시각
     * @return 저장 또는 갱신된 Player 목록
     */
    @Transactional
    public List<Player> upsertPlayers(
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

        /*
         * 선수마다 PostgreSQL 원자적 upsert를 실행합니다.
         *
         * 관심 선수는 최대 5명이므로 개별 쿼리 실행 부담이 작고,
         * 서로 다른 사용자가 같은 선수를 동시에 등록하더라도
         * ON CONFLICT가 player_id 충돌을 안전하게 처리합니다.
         */
        for (BdlPlayer dto : dtoByPlayerId.values()) {

            /*
             * 외부 응답에 team 객체 자체가 있는지 나타냅니다.
             *
             * false:
             * - 외부 응답에서 팀 정보가 제공되지 않음
             * - 기존 players.team_id 유지
             *
             * true:
             * - 외부 응답에서 팀 정보를 제공함
             * - 정상 팀이면 해당 ID로 갱신
             * - 알 수 없는 팀이면 null로 연결 해제
             */
            boolean replaceTeam =
                    dto.team() != null;

            Long resolvedTeamId = null;

            if (replaceTeam) {
                Long externalTeamId =
                        dto.team().id();

                /*
                 * 외부 팀 ID가 로컬 teams 테이블에도 존재할 때만
                 * 외래 키 값으로 사용합니다.
                 *
                 * 로컬에 없는 팀이면 resolvedTeamId는 null로 유지됩니다.
                 */
                if (externalTeamId != null
                        && knownTeamIds.contains(externalTeamId)) {

                    resolvedTeamId =
                            externalTeamId;
                }
            }

            /*
             * null 또는 공백 문자열은 normalizeText()에서 null로 바꿉니다.
             *
             * Repository의 upsert SQL은 null 값에 대해
             * 기존 정상 값을 유지하도록 구성돼 있습니다.
             */
            playerRepository.upsertPlayer(
                    dto.id(),
                    normalizeText(dto.fullName()),
                    normalizeText(dto.firstName()),
                    normalizeText(dto.lastName()),
                    normalizeText(dto.position()),
                    resolvedTeamId,
                    replaceTeam,
                    observedAt
            );
        }

        /*
         * native upsert가 끝난 뒤 실제 DB 상태를 다시 읽습니다.
         *
         * Repository의 findAllById() 반환 순서는 보장되지 않으므로
         * ID 기준 Map으로 변환한 뒤 원래 외부 응답 순서에 맞춰 반환합니다.
         */
        Map<Long, Player> savedPlayersById =
                playerRepository
                        .findAllById(dtoByPlayerId.keySet())
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Player::getId,
                                        Function.identity()
                                )
                        );

        return dtoByPlayerId
                .keySet()
                .stream()
                .map(savedPlayersById::get)
                .filter(player -> player != null)
                .toList();
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
     * 외부 문자열을 DB 저장에 적합한 값으로 정리합니다.
     *
     * - null: null 반환
     * - 공백만 있는 문자열: null 반환
     * - 정상 문자열: 앞뒤 공백 제거
     *
     * null로 전달된 값은 upsert SQL에서
     * 기존 정상 값을 덮어쓰지 않습니다.
     */
    private String normalizeText(
            String value
    ) {
        return hasText(value)
                ? value.trim()
                : null;
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