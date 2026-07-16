package com.pulse.api.user;

import com.pulse.api.user.dto.PlayerSearchResponse;
import com.pulse.api.user.dto.PlayerSearchResultResponse;
import com.pulse.common.client.BaseballDataSource;
import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;


/**
 * 관심 선수 이름 검색 서비스의 단위 테스트입니다.
 *
 * 실제 외부 API와 DB를 사용하지 않고,
 * BaseballDataSource와 Repository를 Mockito 객체로 대체합니다.
 */
@ExtendWith(MockitoExtension.class)
class PlayerSearchServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private BaseballDataSource baseballDataSource;

    private PlayerSearchService playerSearchService;

    /**
     * 각 테스트마다 빈 인메모리 검색 캐시를 생성합니다.
     *
     * PlayerSearchMemoryCache를 Mock으로 만들지 않고 실제 객체로 사용해
     * 다음 흐름도 함께 확인합니다.
     *
     * 캐시 확인
     * → 캐시 미스
     * → 외부 loader 실행
     * → 검색 결과 반환
     */
    @BeforeEach
    void setUp() {
        playerSearchService =
                new PlayerSearchService(
                        playerRepository,
                        teamRepository,
                        baseballDataSource,
                        new PlayerSearchMemoryCache()
                );
    }

    /**
     * 로컬 players 테이블에 같은 선수가 이미 존재하더라도
     * 외부 이름 검색을 생략하지 않는지 검증합니다.
     *
     * 또한 외부 검색 결과를 응답 범위의 기준으로 사용하면서,
     * 외부 응답에 누락된 position만 로컬 정보로 보충하는지 확인합니다.
     *
     * 검색 GET 요청이므로 players 테이블 저장은 발생하면 안 됩니다.
     */
    @Test
    void searchPlayers_shouldUseExternalCoverageAndOnlySupplementFromLocalData() {
        // given
        Long playerId = 208L;

        /*
         * 외부 API에서 검색된 선수입니다.
         *
         * position은 누락된 상태로 설정해
         * 로컬 정보로 보충되는지 확인합니다.
         */
        BdlPlayer externalPlayer =
                new BdlPlayer(
                        playerId,
                        "Shohei Ohtani",
                        "Shohei",
                        "Ohtani",
                        null,
                        null
                );

        /*
         * 같은 playerId의 로컬 Player입니다.
         *
         * 이름은 외부 결과와 다르게 설정하고,
         * position은 정상값으로 설정합니다.
         *
         * 기대 결과:
         * - 이름: 외부 값 우선
         * - 포지션: 외부 값이 없으므로 로컬 값 사용
         */
        Player localPlayer = new Player();

        localPlayer.setId(playerId);
        localPlayer.setFullName("Old Local Name");
        localPlayer.setPosition("DH");
        localPlayer.setTeamId(null);

        /*
         * 검색어는 서비스에서 공백 제거 및 소문자 변환된 뒤
         * 외부 API에 전달됩니다.
         */
        when(baseballDataSource.searchPlayers(
                "ohtani",
                20
        )).thenReturn(
                List.of(externalPlayer)
        );

        /*
         * 외부 결과와 같은 ID의 로컬 선수만 보조 정보로 조회합니다.
         */
        when(playerRepository.findAllById(
                List.of(playerId)
        )).thenReturn(
                List.of(localPlayer)
        );

        /*
         * 외부와 로컬 모두 팀 정보가 없으므로
         * 조회할 팀 ID 목록은 비어 있습니다.
         */
        when(teamRepository.findAllById(
                Set.<Long>of()
        )).thenReturn(
                List.of()
        );

        // when
        PlayerSearchResultResponse result =
                playerSearchService.searchPlayers(
                        "  OHTANI  "
                );

        // then
        /*
         * 외부 검색이 정상적으로 완료됐으므로
         * complete=true여야 합니다.
         */
        assertTrue(result.complete());

        assertEquals(
                1,
                result.players().size()
        );

        PlayerSearchResponse response =
                result.players().get(0);

        assertEquals(
                playerId,
                response.getPlayerId()
        );

        /*
         * 외부 검색 결과가 응답의 기준이므로
         * 로컬의 Old Local Name이 아니라 외부 이름이 반환됩니다.
         */
        assertEquals(
                "Shohei Ohtani",
                response.getFullName()
        );

        /*
         * 외부 position은 null이므로
         * 같은 ID의 로컬 position인 DH로 보충됩니다.
         */
        assertEquals(
                "DH",
                response.getPosition()
        );

        /*
         * 로컬 데이터가 이미 있어도
         * 외부 이름 검색을 반드시 수행해야 합니다.
         */
        verify(baseballDataSource).searchPlayers(
                "ohtani",
                20
        );

        verify(playerRepository).findAllById(
                List.of(playerId)
        );

        /*
         * GET /api/players 검색 중에는
         * players 테이블을 수정하면 안 됩니다.
         */
        verify(
                playerRepository,
                never()
        ).save(any(Player.class));

        verify(
                playerRepository,
                never()
        ).saveAll(anyList());
    }


    /**
     * 외부 선수 이름 검색이 실패하면
     * 로컬 players 검색 결과를 반환하고
     * complete=false로 표시하는지 검증합니다.
     *
     * 로컬 players는 전체 MLB 선수 명단이 아니므로,
     * 결과가 존재하더라도 완전한 검색 결과라고 표시하면 안 됩니다.
     */
    @Test
    void searchPlayers_shouldReturnIncompleteLocalFallbackWhenExternalSearchFails() {
        // given
        Long playerId = 208L;

        /*
         * 외부 API 장애 시 대신 반환할 로컬 선수입니다.
         */
        Player localPlayer = new Player();

        localPlayer.setId(playerId);
        localPlayer.setFullName("Shohei Ohtani");
        localPlayer.setPosition("DH");
        localPlayer.setTeamId(null);

        /*
         * 외부 선수 이름 검색에서 네트워크 오류가
         * 발생했다고 가정합니다.
         */
        when(baseballDataSource.searchPlayers(
                "ohtani",
                20
        )).thenThrow(
                new RuntimeException(
                        "외부 선수 검색 API 연결 실패"
                )
        );

        /*
         * 외부 검색 실패 후 실행되는 로컬 부분 검색입니다.
         */
        when(playerRepository
                .findByFullNameIsNotNullAndFullNameContainingIgnoreCaseOrderByFullNameAsc(
                        "ohtani",
                        org.springframework.data.domain.PageRequest.of(
                                0,
                                20
                        )
                ))
                .thenReturn(
                        List.of(localPlayer)
                );

        /*
         * 로컬 선수에게 팀 정보가 없으므로
         * 조회할 Team 목록도 없습니다.
         */
        when(teamRepository.findAllById(
                Set.<Long>of()
        )).thenReturn(
                List.of()
        );

        // when
        PlayerSearchResultResponse result =
                playerSearchService.searchPlayers(
                        "OHTANI"
                );

        // then
        /*
         * 외부 검색에 실패했으므로
         * 결과가 완전하지 않다는 표시가 필요합니다.
         */
        assertFalse(result.complete());

        assertEquals(
                1,
                result.players().size()
        );

        PlayerSearchResponse response =
                result.players().get(0);

        assertEquals(
                playerId,
                response.getPlayerId()
        );

        assertEquals(
                "Shohei Ohtani",
                response.getFullName()
        );

        assertEquals(
                "DH",
                response.getPosition()
        );

        /*
         * 외부 검색을 먼저 시도해야 합니다.
         */
        verify(baseballDataSource).searchPlayers(
                "ohtani",
                20
        );

        /*
         * 외부 검색 실패 후에만 로컬 검색이 실행됩니다.
         */
        verify(playerRepository)
                .findByFullNameIsNotNullAndFullNameContainingIgnoreCaseOrderByFullNameAsc(
                        "ohtani",
                        org.springframework.data.domain.PageRequest.of(
                                0,
                                20
                        )
                );

        /*
         * 폴백 검색 역시 읽기 전용이므로
         * players 테이블 저장은 없어야 합니다.
         */
        verify(
                playerRepository,
                never()
        ).save(any(Player.class));

        verify(
                playerRepository,
                never()
        ).saveAll(anyList());
    }


    /**
     * 같은 검색어를 반복해서 조회하면
     * 첫 번째 요청의 결과를 인메모리 캐시에서 재사용하고,
     * 외부 선수 검색 API는 한 번만 호출하는지 검증합니다.
     *
     * 검색 결과를 프론트 응답으로 변환하는 과정의 로컬 보조 조회는
     * 요청마다 실행될 수 있지만, 비용이 큰 외부 API 호출은 반복하지 않습니다.
     */
    @Test
    void searchPlayers_shouldReuseCachedResultForSameKeyword() {
        // given
        Long playerId = 208L;

        BdlPlayer externalPlayer =
                new BdlPlayer(
                        playerId,
                        "Shohei Ohtani",
                        "Shohei",
                        "Ohtani",
                        "DH",
                        null
                );

        when(baseballDataSource.searchPlayers(
                "ohtani",
                20
        )).thenReturn(
                List.of(externalPlayer)
        );

        /*
         * 동일 ID의 로컬 보조 정보는 없는 상황입니다.
         */
        when(playerRepository.findAllById(
                List.of(playerId)
        )).thenReturn(
                List.of()
        );

        when(teamRepository.findAllById(
                Set.<Long>of()
        )).thenReturn(
                List.of()
        );

        // when
        PlayerSearchResultResponse firstResult =
                playerSearchService.searchPlayers(
                        "OHTANI"
                );

        PlayerSearchResultResponse secondResult =
                playerSearchService.searchPlayers(
                        "  ohtani  "
                );

        // then
        assertTrue(firstResult.complete());
        assertTrue(secondResult.complete());

        assertEquals(
                1,
                firstResult.players().size()
        );

        assertEquals(
                1,
                secondResult.players().size()
        );

        assertEquals(
                playerId,
                firstResult.players()
                        .get(0)
                        .getPlayerId()
        );

        assertEquals(
                playerId,
                secondResult.players()
                        .get(0)
                        .getPlayerId()
        );

        /*
         * 대소문자와 앞뒤 공백이 달라도 모두 "ohtani"로 정규화되므로,
         * 외부 API는 첫 번째 요청에서 한 번만 실행돼야 합니다.
         */
        verify(
                baseballDataSource,
                times(1)
        ).searchPlayers(
                "ohtani",
                20
        );

        /*
         * 검색 GET 요청은 캐시 사용 여부와 관계없이
         * players 테이블을 수정하지 않습니다.
         */
        verify(
                playerRepository,
                never()
        ).save(any(Player.class));

        verify(
                playerRepository,
                never()
        ).saveAll(anyList());
    }


    /**
     * 검색어가 최소 길이인 2글자보다 짧으면
     * 외부 API와 DB를 조회하지 않고 빈 결과를 반환하는지 검증합니다.
     *
     * 한 글자 검색은 결과 범위가 지나치게 넓어
     * 외부 API 쿼터를 불필요하게 소모할 수 있으므로 차단합니다.
     */
    @Test
    void searchPlayers_shouldReturnEmptyResultWithoutLookupWhenKeywordIsTooShort() {
        // when
        PlayerSearchResultResponse result =
                playerSearchService.searchPlayers(
                        " A "
                );

        // then
        /*
         * 외부 장애가 발생한 것은 아니므로
         * complete=true로 반환합니다.
         */
        assertTrue(result.complete());

        assertTrue(result.players().isEmpty());

        /*
         * 검색어가 한 글자뿐이므로
         * 외부 API와 DB를 전혀 조회하면 안 됩니다.
         */
        verifyNoInteractions(baseballDataSource);
        verifyNoInteractions(playerRepository);
        verifyNoInteractions(teamRepository);
    }


    /**
     * 외부 선수 검색이 정상적으로 완료됐지만
     * 일치하는 선수가 없는 경우를 검증합니다.
     *
     * 이 경우는 외부 API 장애가 아니므로:
     *
     * - complete=true
     * - players=[]
     *
     * 로 반환해야 합니다.
     *
     * 또한 빈 검색 결과도 짧은 시간 캐시하므로,
     * 같은 검색어를 반복해도 외부 API를 다시 호출하지 않아야 합니다.
     */
    @Test
    void searchPlayers_shouldCacheSuccessfulEmptyResult() {
        // given
        /*
         * 외부 API 호출 자체는 정상적으로 성공했지만
         * 검색 결과가 없는 상황입니다.
         */
        when(baseballDataSource.searchPlayers(
                "zzunknown",
                20
        )).thenReturn(
                List.of()
        );

        // when
        PlayerSearchResultResponse firstResult =
                playerSearchService.searchPlayers(
                        "ZZUNKNOWN"
                );

        PlayerSearchResultResponse secondResult =
                playerSearchService.searchPlayers(
                        "  zzunknown  "
                );

        // then
        /*
         * 외부 API 호출은 성공했으므로
         * 결과가 비어 있어도 complete=true입니다.
         */
        assertTrue(firstResult.complete());
        assertTrue(secondResult.complete());

        assertTrue(firstResult.players().isEmpty());
        assertTrue(secondResult.players().isEmpty());

        /*
         * 빈 결과도 캐시에 저장되므로
         * 같은 정규화 검색어의 외부 호출은 한 번만 실행됩니다.
         */
        verify(
                baseballDataSource,
                times(1)
        ).searchPlayers(
                "zzunknown",
                20
        );

        /*
         * 외부 결과가 비어 있으면
         * 같은 ID의 로컬 선수나 팀을 보조 조회할 필요도 없습니다.
         */
        verifyNoInteractions(playerRepository);
        verifyNoInteractions(teamRepository);
    }
}