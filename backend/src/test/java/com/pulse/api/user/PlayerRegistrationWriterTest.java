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
        Long unknownExternalTeamId = 999L;

        Instant observedAt =
                Instant.parse("2026-07-15T08:00:00Z");

        /*
         * native upsert가 끝난 뒤 DB에서 다시 조회된 결과를 가정합니다.
         *
         * Mockito 테스트에서는 실제 SQL이 실행되지 않으므로,
         * 최종 저장 상태의 Player 객체를 직접 준비합니다.
         */
        Player savedPlayer = new Player();

        savedPlayer.setId(playerId);
        savedPlayer.setFullName("Shohei Ohtani");
        savedPlayer.setFirstName("Shohei");
        savedPlayer.setLastName("Ohtani");
        savedPlayer.setPosition("DH");

        /*
         * 외부 팀은 존재하지만 로컬 teams에는 없으므로
         * 최종 DB의 team_id는 null이어야 합니다.
         */
        savedPlayer.setTeamId(null);
        savedPlayer.setUpdatedAt(observedAt);

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
                List.of(savedPlayer)
        );

        /*
         * 외부 팀 ID 999에 대응하는 로컬 Team은 없습니다.
         */
        when(teamRepository.findAllById(
                Set.of(unknownExternalTeamId)
        )).thenReturn(
                List.of()
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
        assertNull(savedPlayer.getTeamId());

        /*
         * 외부에서 정상적으로 받은 선수 이름과 포지션은 반영됩니다.
         */
        assertEquals(
                "Shohei Ohtani",
                savedPlayer.getFullName()
        );

        assertEquals(
                "DH",
                savedPlayer.getPosition()
        );

        /*
         * 선수 정보를 확인한 시각도 updatedAt에 반영돼야 합니다.
         */
        assertEquals(
                observedAt,
                savedPlayer.getUpdatedAt()
        );

        assertEquals(
                List.of(savedPlayer),
                savedPlayers
        );

        /*
         * 팀 객체는 전달됐지만 로컬에 없는 팀이므로
         * teamId=null, replaceTeam=true로 upsert해야 합니다.
         */
        verify(playerRepository).upsertPlayer(
                playerId,
                "Shohei Ohtani",
                "Shohei",
                "Ohtani",
                "DH",
                null,
                true,
                observedAt
        );

        /*
         * upsert 이후 실제 DB 결과를 다시 조회해야 합니다.
         */
        verify(playerRepository).findAllById(
                Set.of(playerId)
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
        Player savedPlayer = new Player();

        savedPlayer.setId(playerId);
        savedPlayer.setFullName("Shohei Ohtani");
        savedPlayer.setFirstName("Shohei");
        savedPlayer.setLastName("Ohtani");
        savedPlayer.setPosition("DH");
        savedPlayer.setTeamId(existingTeamId);
        savedPlayer.setUpdatedAt(observedAt);

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
                List.of(savedPlayer)
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
                savedPlayer.getTeamId()
        );

        /*
         * 외부 null 또는 공백 값도
         * 기존 정상 선수 정보를 덮어쓰면 안 됩니다.
         */
        assertEquals(
                "Shohei",
                savedPlayer.getFirstName()
        );

        assertEquals(
                "Ohtani",
                savedPlayer.getLastName()
        );

        assertEquals(
                "DH",
                savedPlayer.getPosition()
        );

        /*
         * 선수 정보를 다시 확인한 시각은 갱신됩니다.
         */
        assertEquals(
                observedAt,
                savedPlayer.getUpdatedAt()
        );

        assertEquals(
                List.of(savedPlayer),
                savedPlayers
        );

        verify(playerRepository).findAllById(
                Set.of(playerId)
        );

        verify(teamRepository).findAllById(
                Set.<Long>of()
        );

        /*
         * 외부 응답에 team 객체 자체가 없으므로
         * 기존 team_id를 유지하도록 replaceTeam=false를 전달해야 합니다.
         *
         * null 또는 공백인 값은 normalizeText()에서 null로 변환됩니다.
         */
        verify(playerRepository).upsertPlayer(
                playerId,
                "Shohei Ohtani",
                null,
                null,
                null,
                null,
                false,
                observedAt
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
         * native upsert가 끝난 뒤 DB에서 다시 조회된
         * 최종 선수 상태를 가정합니다.
         */
        Player savedPlayer = new Player();

        savedPlayer.setId(playerId);
        savedPlayer.setFullName("Shohei Ohtani");
        savedPlayer.setFirstName("Shohei");
        savedPlayer.setLastName("Ohtani");
        savedPlayer.setPosition("DH");
        savedPlayer.setTeamId(knownTeamId);
        savedPlayer.setUpdatedAt(observedAt);

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

        /*
         * native upsert 실행 후 최종 DB 상태를 다시 조회하는 상황입니다.
         */
        when(playerRepository.findAllById(
                Set.of(playerId)
        )).thenReturn(
                List.of(savedPlayer)
        );

        when(teamRepository.findAllById(
                Set.of(knownTeamId)
        )).thenReturn(
                List.of(knownTeam)
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
                savedPlayer.getTeamId()
        );

        assertEquals(
                "Shohei Ohtani",
                savedPlayer.getFullName()
        );

        assertEquals(
                "DH",
                savedPlayer.getPosition()
        );

        assertEquals(
                observedAt,
                savedPlayer.getUpdatedAt()
        );

        assertEquals(
                List.of(savedPlayer),
                savedPlayers
        );

        verify(playerRepository).findAllById(
                Set.of(playerId)
        );

        verify(teamRepository).findAllById(
                Set.of(knownTeamId)
        );

        /*
         * 외부 팀 ID가 로컬 teams 테이블에도 존재하므로
         * teamId=14, replaceTeam=true로 upsert해야 합니다.
         */
        verify(playerRepository).upsertPlayer(
                playerId,
                "Shohei Ohtani",
                "Shohei",
                "Ohtani",
                "DH",
                knownTeamId,
                true,
                observedAt
        );
    }
}