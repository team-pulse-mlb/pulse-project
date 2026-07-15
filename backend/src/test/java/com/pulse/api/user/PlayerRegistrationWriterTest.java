package com.pulse.api.user;

import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.common.client.BdlDtos.BdlPlayer.TeamRef;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.Team;
import com.pulse.domain.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관심 선수 등록 시 players 테이블을 저장·갱신하는
 * PlayerRegistrationWriter의 단위 테스트입니다.
 *
 * 실제 DB를 사용하지 않고 PlayerRepository와 TeamRepository를
 * Mockito 객체로 대체해 Writer 로직만 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class PlayerRegistrationWriterTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private TeamRepository teamRepository;

    /*
     * 위에서 만든 Repository Mock을 생성자로 주입해
     * 테스트 대상 Writer를 생성합니다.
     */
    @InjectMocks
    private PlayerRegistrationWriter playerRegistrationWriter;

    /**
     * 외부 선수 응답에는 팀 ID가 있지만,
     * 그 팀이 로컬 teams 테이블에 없다면
     * 기존 Player의 teamId를 null로 변경하는지 검증합니다.
     *
     * 예:
     * - 기존 DB teamId: 14
     * - 외부 응답 teamId: 999
     * - 로컬 teams에 999 없음
     * - 저장 결과 teamId: null
     */
    @Test
    void upsertPlayers_shouldClearExistingTeamWhenExternalTeamIsUnknownLocally() {
        // given
        Long playerId = 208L;
        Long existingTeamId = 14L;
        Long unknownExternalTeamId = 999L;

        Instant observedAt =
                Instant.parse("2026-07-15T08:00:00Z");

        /*
         * players 테이블에 이미 존재하는 선수라고 가정합니다.
         */
        Player existingPlayer = new Player();

        existingPlayer.setId(playerId);
        existingPlayer.setFullName("Shohei Ohtani");
        existingPlayer.setTeamId(existingTeamId);

        /*
         * 외부 API에는 팀 ID 999가 포함되어 있지만,
         * 해당 팀은 로컬 teams 테이블에 존재하지 않는 상황입니다.
         */
        BdlPlayer externalPlayer =
                new BdlPlayer(
                        playerId,
                        "Shohei Ohtani",
                        "Shohei",
                        "Ohtani",
                        "DH",
                        new TeamRef(unknownExternalTeamId)
                );

        /*
         * 해당 선수는 기존 players 테이블에 존재합니다.
         */
        when(playerRepository.findAllById(
                Set.of(playerId)
        )).thenReturn(
                List.of(existingPlayer)
        );

        /*
         * 외부 팀 ID 999에 대응하는 로컬 Team은 없습니다.
         */
        when(teamRepository.findAllById(
                Set.of(unknownExternalTeamId)
        )).thenReturn(
                List.of()
        );

        /*
         * Writer가 변경한 Player 객체를 정상 저장했다고 가정합니다.
         */
        when(playerRepository.saveAll(anyList()))
                .thenReturn(
                        List.of(existingPlayer)
                );

        // when
        List<Player> savedPlayers =
                playerRegistrationWriter.upsertPlayers(
                        List.of(externalPlayer),
                        observedAt
                );

        // then
        /*
         * 로컬 teams에 없는 팀은 외래 키로 연결할 수 없으므로
         * 기존 teamId가 그대로 남지 않고 null이 되어야 합니다.
         */
        assertNull(existingPlayer.getTeamId());

        /*
         * 외부에서 정상적으로 받은 선수 이름과 포지션은 반영됩니다.
         */
        assertEquals(
                "Shohei Ohtani",
                existingPlayer.getFullName()
        );

        assertEquals(
                "DH",
                existingPlayer.getPosition()
        );

        /*
         * 선수 정보를 확인한 시각도 updatedAt에 반영돼야 합니다.
         */
        assertEquals(
                observedAt,
                existingPlayer.getUpdatedAt()
        );

        assertEquals(
                List.of(existingPlayer),
                savedPlayers
        );

        verify(playerRepository).findAllById(
                Set.of(playerId)
        );

        verify(teamRepository).findAllById(
                Set.of(unknownExternalTeamId)
        );

        verify(playerRepository).saveAll(
                List.of(existingPlayer)
        );
    }


    /**
     * 외부 선수 응답에 팀 정보 자체가 없는 경우에는
     * 기존 Player의 정상적인 teamId를 유지하는지 검증합니다.
     *
     * 또한 외부 응답의 이름·포지션 값이 null 또는 공백이면
     * 기존 정상 값을 덮어쓰지 않는지도 함께 확인합니다.
     *
     * 예:
     * - 기존 teamId: 14
     * - 외부 team: null
     * - 저장 결과 teamId: 14 유지
     */
    @Test
    void upsertPlayers_shouldPreserveExistingValuesWhenExternalFieldsAreMissing() {
        // given
        Long playerId = 208L;
        Long existingTeamId = 14L;

        Instant observedAt =
                Instant.parse("2026-07-15T09:00:00Z");

        /*
         * players 테이블에 이미 정상적인 선수 정보가
         * 저장돼 있다고 가정합니다.
         */
        Player existingPlayer = new Player();

        existingPlayer.setId(playerId);
        existingPlayer.setFullName("Shohei Ohtani");
        existingPlayer.setFirstName("Shohei");
        existingPlayer.setLastName("Ohtani");
        existingPlayer.setPosition("DH");
        existingPlayer.setTeamId(existingTeamId);

        /*
         * 외부 응답에는 선수 식별에 필요한 fullName만 있고,
         * 나머지 정보는 null 또는 공백입니다.
         *
         * team 객체 자체도 null이므로
         * 기존 teamId를 유지해야 합니다.
         */
        BdlPlayer externalPlayer =
                new BdlPlayer(
                        playerId,
                        "Shohei Ohtani",
                        null,
                        "",
                        "   ",
                        null
                );

        when(playerRepository.findAllById(
                Set.of(playerId)
        )).thenReturn(
                List.of(existingPlayer)
        );

        /*
         * 외부 응답에 팀 정보가 없으므로
         * 조회할 팀 ID 목록도 빈 Set입니다.
         */
        when(teamRepository.findAllById(
                Set.<Long>of()
        )).thenReturn(
                List.of()
        );

        when(playerRepository.saveAll(anyList()))
                .thenReturn(
                        List.of(existingPlayer)
                );

        // when
        List<Player> savedPlayers =
                playerRegistrationWriter.upsertPlayers(
                        List.of(externalPlayer),
                        observedAt
                );

        // then
        /*
         * 외부 team 객체 자체가 없으면
         * 기존의 정상 teamId를 유지해야 합니다.
         */
        assertEquals(
                existingTeamId,
                existingPlayer.getTeamId()
        );

        /*
         * 외부 null 또는 공백 값도
         * 기존 정상 선수 정보를 덮어쓰면 안 됩니다.
         */
        assertEquals(
                "Shohei",
                existingPlayer.getFirstName()
        );

        assertEquals(
                "Ohtani",
                existingPlayer.getLastName()
        );

        assertEquals(
                "DH",
                existingPlayer.getPosition()
        );

        /*
         * 선수 정보를 다시 확인한 시각은 갱신됩니다.
         */
        assertEquals(
                observedAt,
                existingPlayer.getUpdatedAt()
        );

        assertEquals(
                List.of(existingPlayer),
                savedPlayers
        );

        verify(playerRepository).findAllById(
                Set.of(playerId)
        );

        verify(teamRepository).findAllById(
                Set.<Long>of()
        );

        verify(playerRepository).saveAll(
                List.of(existingPlayer)
        );
    }


    /**
     * 외부 선수 응답의 팀 ID가 로컬 teams 테이블에도 존재하면
     * Player의 teamId를 해당 팀으로 정상 갱신하는지 검증합니다.
     *
     * 예:
     * - 기존 teamId: null
     * - 외부 teamId: 14
     * - 로컬 teams에도 14 존재
     * - 저장 결과 teamId: 14
     */
    @Test
    void upsertPlayers_shouldSetTeamWhenExternalTeamIsKnownLocally() {
        // given
        Long playerId = 208L;
        Long knownTeamId = 14L;

        Instant observedAt =
                Instant.parse("2026-07-15T10:00:00Z");

        /*
         * 기존 Player에는 아직 팀 연결이 없다고 가정합니다.
         */
        Player existingPlayer = new Player();

        existingPlayer.setId(playerId);
        existingPlayer.setFullName("Shohei Ohtani");
        existingPlayer.setTeamId(null);

        /*
         * 외부 API에서 teamId=14를 받은 상황입니다.
         */
        BdlPlayer externalPlayer =
                new BdlPlayer(
                        playerId,
                        "Shohei Ohtani",
                        "Shohei",
                        "Ohtani",
                        "DH",
                        new TeamRef(knownTeamId)
                );

        /*
         * teams 테이블에도 teamId=14가 실제로 존재합니다.
         */
        Team knownTeam =
                org.mockito.Mockito.mock(Team.class);

        when(knownTeam.getTeamId())
                .thenReturn(knownTeamId);

        when(playerRepository.findAllById(
                Set.of(playerId)
        )).thenReturn(
                List.of(existingPlayer)
        );

        when(teamRepository.findAllById(
                Set.of(knownTeamId)
        )).thenReturn(
                List.of(knownTeam)
        );

        when(playerRepository.saveAll(anyList()))
                .thenReturn(
                        List.of(existingPlayer)
                );

        // when
        List<Player> savedPlayers =
                playerRegistrationWriter.upsertPlayers(
                        List.of(externalPlayer),
                        observedAt
                );

        // then
        /*
         * 외부 팀이 로컬 teams에도 존재하므로
         * 해당 teamId가 Player에 정상 연결돼야 합니다.
         */
        assertEquals(
                knownTeamId,
                existingPlayer.getTeamId()
        );

        assertEquals(
                "Shohei Ohtani",
                existingPlayer.getFullName()
        );

        assertEquals(
                "DH",
                existingPlayer.getPosition()
        );

        assertEquals(
                observedAt,
                existingPlayer.getUpdatedAt()
        );

        assertEquals(
                List.of(existingPlayer),
                savedPlayers
        );

        verify(playerRepository).findAllById(
                Set.of(playerId)
        );

        verify(teamRepository).findAllById(
                Set.of(knownTeamId)
        );

        verify(playerRepository).saveAll(
                List.of(existingPlayer)
        );
    }
}