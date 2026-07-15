package com.pulse.api.user;

import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import com.pulse.api.user.domain.UserFavoritePlayerRepository;
import com.pulse.api.user.exception.InvalidFavoritePlayerException;
import com.pulse.domain.PlayerRepository;
import com.pulse.api.user.domain.UserFavoriteTeam;
import com.pulse.api.user.domain.UserFavoriteTeamRepository;
import com.pulse.api.user.domain.UserSetting;
import com.pulse.api.user.domain.UserSettingRepository;
import com.pulse.api.user.dto.UserPreferenceUpdateRequest;
import com.pulse.api.user.exception.PlayerLookupUnavailableException;
import com.pulse.domain.Team;
import com.pulse.domain.TeamRepository;
import com.pulse.common.client.BaseballDataSource;
import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.domain.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UserPreferenceService 단위 테스트입니다.
 *
 * 실제 DB와 Spring Context를 실행하지 않고,
 * Repository를 Mockito 객체로 대체해 서비스 로직만 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserSettingRepository userSettingRepository;

    @Mock
    private UserFavoriteTeamRepository userFavoriteTeamRepository;

    @Mock
    private TeamRepository teamRepository;

    /*
     * 관심 선수 기능 추가 후 UserPreferenceService 생성자에 추가된 의존성입니다.
     *
     * 이 테스트는 관심팀 저장 순서를 검증하지만,
     * 서비스가 기존 관심 선수 설정을 유지하기 위해
     * UserFavoritePlayerRepository도 호출하므로 Mock이 필요합니다.
     */
    @Mock
    private UserFavoritePlayerRepository userFavoritePlayerRepository;

    /*
     * 현재 테스트 요청에는 selectedPlayerIds가 없어서 직접 호출되지는 않지만,
     * UserPreferenceService 생성자 의존성을 온전하게 구성하기 위해 Mock으로 등록합니다.
     */
    @Mock
    private PlayerRepository playerRepository;

    /*
     * 검색 캐시에 없는 선수 ID를
     * balldontlie에서 정확 조회할 때 사용하는 외부 데이터 경계입니다.
     */
    @Mock
    private BaseballDataSource baseballDataSource;

    /*
     * 직전 선수 검색으로 확인된 선수 정보를
     * playerId 기준으로 다시 조회하는 인메모리 캐시입니다.
     */
    @Mock
    private PlayerSearchMemoryCache playerSearchMemoryCache;

    /*
     * 검증이 끝난 관심 선수 정보를
     * players 테이블에 저장하거나 갱신하는 Writer입니다.
     */
    @Mock
    private PlayerRegistrationWriter playerRegistrationWriter;

    /**
     * 위에서 선언한 Mock Repository들을 주입해
     * 테스트 대상 서비스를 생성합니다.
     */
    @InjectMocks
    private UserPreferenceService userPreferenceService;

    /**
     * 기존 관심팀과 새 관심팀이 일부 겹치는 경우에도
     * 기존 데이터 삭제가 DB에 먼저 반영된 뒤
     * 새 관심팀 저장이 실행되는지 검증합니다.
     *
     * 예시:
     * 기존 관심팀: NYY(147), LAD(119)
     * 변경 관심팀: LAD(119), LAA(110)
     *
     * LAD가 기존 목록과 새 목록에 모두 존재하므로
     * DELETE보다 INSERT가 먼저 실행되면
     * (user_id, team_id) 복합 PK 충돌이 발생할 수 있습니다.
     *
     * 따라서 다음 호출 순서를 반드시 보장해야 합니다.
     *
     * deleteByMemberUserId()
     * → flush()
     * → saveAll()
     */
    @Test
    @SuppressWarnings("unchecked")
    void updateMyPreferences_shouldFlushDeleteBeforeSavingOverlappingTeams() {
        // given
        String requestEmail = "USER@EXAMPLE.COM";
        String normalizedEmail = "user@example.com";
        Long userId = 1L;

        /*
         * 기존 관심팀 중 유지되는 LAD 팀입니다.
         */
        Team retainedTeam = org.mockito.Mockito.mock(Team.class);

        /*
         * 새로 추가하는 LAA 팀입니다.
         */
        Team addedTeam = org.mockito.Mockito.mock(Team.class);

        Member member = org.mockito.Mockito.mock(Member.class);
        UserSetting userSetting =
                org.mockito.Mockito.mock(UserSetting.class);

        when(member.getUserId()).thenReturn(userId);

        /*
         * updateMyPreferences()는 같은 사용자의 동시 저장 요청을
         * 순차 처리하기 위해 잠금 조회 메서드를 사용합니다.
         */
        when(memberRepository.findByEmailForUpdate(normalizedEmail))
                .thenReturn(Optional.of(member));

        when(userSettingRepository.findById(userId))
                .thenReturn(Optional.of(userSetting));

        when(retainedTeam.getTeamId()).thenReturn(119L);
        when(addedTeam.getTeamId()).thenReturn(110L);

        /*
         * 요청한 팀 ID 순서대로 실제 팀이 조회됐다고 가정합니다.
         */
        when(teamRepository.findAllById(List.of(119L, 110L)))
                .thenReturn(List.of(retainedTeam, addedTeam));

        /*
         * 저장 완료 후 최종 관심팀 조회 결과입니다.
         *
         * 이번 테스트의 핵심은 응답 내용보다
         * DELETE → flush → INSERT 호출 순서이므로
         * 빈 목록으로 반환해도 됩니다.
         */
        when(userFavoriteTeamRepository.findByMemberUserId(userId))
                .thenReturn(List.of());

        /*
         * 이 테스트는 관심팀 저장 순서만 검증하므로
         * 기존 관심 선수는 없는 상태로 설정합니다.
         *
         * selectedPlayerIds가 null이면 서비스는 관심 선수를 변경하지 않고
         * 현재 목록을 조회해 그대로 응답에 포함합니다.
         */
        when(userFavoritePlayerRepository
                .findByMemberUserIdOrderByCreatedAtAsc(userId))
                .thenReturn(List.of());

        UserPreferenceUpdateRequest request =
                new UserPreferenceUpdateRequest();

        request.setSelectedTeamIds(
                List.of(119L, 110L)
        );

        UserPreferenceUpdateRequest.NotificationSettingsRequest
                notificationSettings =
                new UserPreferenceUpdateRequest.NotificationSettingsRequest();

        notificationSettings.setGameStart(true);
        notificationSettings.setSurge(true);
        notificationSettings.setGameSwitch(true);

        request.setNotificationSettings(notificationSettings);

        // when
        userPreferenceService.updateMyPreferences(
                requestEmail,
                request
        );

        // then
        /*
         * 하나의 Repository에서 호출된 메서드들의 순서를 검증합니다.
         */
        InOrder repositoryCallOrder =
                inOrder(userFavoriteTeamRepository);

        repositoryCallOrder.verify(userFavoriteTeamRepository)
                .deleteByMemberUserId(userId);

        repositoryCallOrder.verify(userFavoriteTeamRepository)
                .flush();

        /*
         * saveAll에 실제로 전달된 관심팀 엔티티도 함께 가져옵니다.
         */
        ArgumentCaptor<Iterable<UserFavoriteTeam>>
                savedFavoriteTeamsCaptor =
                ArgumentCaptor.forClass(Iterable.class);

        repositoryCallOrder.verify(userFavoriteTeamRepository)
                .saveAll(savedFavoriteTeamsCaptor.capture());

        /*
         * 새 관심팀으로 LAD(119), LAA(110)가
         * 선택 순서대로 저장됐는지 확인합니다.
         */
        List<Long> savedTeamIds = StreamSupport.stream(
                        savedFavoriteTeamsCaptor
                                .getValue()
                                .spliterator(),
                        false
                )
                .map(UserFavoriteTeam::getTeam)
                .map(Team::getTeamId)
                .toList();

        assertEquals(
                List.of(119L, 110L),
                savedTeamIds
        );

        /*
         * 전달된 알림 설정이 엔티티 변경 메서드로 전달됐는지도 확인합니다.
         */
        verify(userSetting).updatePreferences(
                true,
                true,
                true
        );
    }


    /**
     * 관심 선수 ID가 직전 검색 캐시에 존재하면
     * 외부 선수 상세 API를 다시 호출하지 않고 등록하는지 검증합니다.
     *
     * 검증하려는 흐름:
     *
     * selectedPlayerIds=[208]
     * → 검색 캐시에서 208 확인
     * → 외부 getPlayers() 호출 생략
     * → 확인된 선수만 players에 upsert
     * → 관심 선수 관계 저장
     */
    @Test
    void updateMyPreferences_shouldUseSearchCacheWithoutExternalLookup() {
        // given
        String requestEmail = "USER@EXAMPLE.COM";
        String normalizedEmail = "user@example.com";
        Long userId = 1L;
        Long playerId = 208L;

        Member member =
                org.mockito.Mockito.mock(Member.class);

        UserSetting userSetting =
                org.mockito.Mockito.mock(UserSetting.class);

        /*
         * 검색 API에서 받은 뒤 인메모리 캐시에 보관된
         * balldontlie 선수 정보입니다.
         */
        BdlPlayer cachedPlayer =
                new BdlPlayer(
                        playerId,
                        "Shohei Ohtani",
                        "Shohei",
                        "Ohtani",
                        "DH",
                        null
                );

        /*
         * PlayerRegistrationWriter가 players 테이블에
         * 저장하거나 갱신해서 반환한 Player 엔티티입니다.
         */
        Player savedPlayer =
                org.mockito.Mockito.mock(Player.class);

        when(member.getUserId())
                .thenReturn(userId);

        when(savedPlayer.getId())
                .thenReturn(playerId);

        /*
         * PUT 요청은 사용자 단위 잠금 조회를 사용합니다.
         */
        when(memberRepository
                .findByEmailForUpdate(normalizedEmail))
                .thenReturn(Optional.of(member));

        when(userSettingRepository.findById(userId))
                .thenReturn(Optional.of(userSetting));

        /*
         * 이번 테스트에서는 관심팀을 선택하지 않습니다.
         */
        when(userFavoriteTeamRepository
                .findByMemberUserId(userId))
                .thenReturn(List.of());

        /*
         * 요청한 playerId가 검색 캐시에 존재하는 상황입니다.
         */
        when(playerSearchMemoryCache
                .findPlayersByIds(List.of(playerId)))
                .thenReturn(
                        Map.of(
                                playerId,
                                cachedPlayer
                        )
                );

        /*
         * 캐시에서 확인한 선수 정보를 Writer가
         * 정상적으로 저장했다고 가정합니다.
         */
        when(playerRegistrationWriter.upsertPlayers(
                eq(List.of(cachedPlayer)),
                any(Instant.class)
        )).thenReturn(List.of(savedPlayer));

        /*
         * 첫 번째 조회:
         * - 기존 관심 선수 목록
         *
         * 두 번째 조회:
         * - 저장 완료 후 최종 관심 선수 목록
         */
        when(userFavoritePlayerRepository
                .findByMemberUserIdOrderByCreatedAtAsc(userId))
                .thenReturn(
                        List.of(),
                        List.of()
                );

        UserPreferenceUpdateRequest request =
                new UserPreferenceUpdateRequest();

        request.setSelectedTeamIds(List.of());
        request.setSelectedPlayerIds(
                List.of(playerId)
        );

        // when
        userPreferenceService.updateMyPreferences(
                requestEmail,
                request
        );

        // then
        /*
         * 먼저 검색 캐시에서 선수 정보를 확인해야 합니다.
         */
        verify(playerSearchMemoryCache)
                .findPlayersByIds(
                        List.of(playerId)
                );

        /*
         * 캐시에 선수가 있으므로 외부 선수 상세 조회는
         * 실행되지 않아야 합니다.
         */
        verify(
                baseballDataSource,
                never()
        ).getPlayers(anyList());

        /*
         * 캐시에서 확인된 선수만 등록 Writer로 전달합니다.
         */
        verify(playerRegistrationWriter)
                .upsertPlayers(
                        eq(List.of(cachedPlayer)),
                        any(Instant.class)
                );

        /*
         * 검증·upsert된 선수로 관심 선수 관계를 저장해야 합니다.
         */
        verify(userFavoritePlayerRepository)
                .saveAll(anyList());
    }


    /**
     * 관심 선수 ID가 검색 캐시에 없으면
     * 외부 선수 상세 API로 실제 선수인지 확인한 뒤
     * 등록하는지 검증합니다.
     *
     * 검증 흐름:
     *
     * selectedPlayerIds=[208]
     * → 검색 캐시에 208 없음
     * → baseballDataSource.getPlayers([208]) 호출
     * → 외부에서 선수 확인
     * → players 테이블 upsert
     * → 관심 선수 관계 저장
     */
    @Test
    void updateMyPreferences_shouldFetchPlayerWhenSearchCacheMisses() {
        // given
        String requestEmail = "USER@EXAMPLE.COM";
        String normalizedEmail = "user@example.com";

        Long userId = 1L;
        Long playerId = 208L;

        Member member =
                org.mockito.Mockito.mock(Member.class);

        UserSetting userSetting =
                org.mockito.Mockito.mock(UserSetting.class);

        /*
         * 검색 캐시에는 없지만,
         * 외부 getPlayers(ids) 조회로 확인된 선수입니다.
         */
        BdlPlayer fetchedPlayer =
                new BdlPlayer(
                        playerId,
                        "Shohei Ohtani",
                        "Shohei",
                        "Ohtani",
                        "DH",
                        null
                );

        /*
         * PlayerRegistrationWriter가 players 테이블에
         * 저장 또는 갱신한 뒤 반환하는 Player 엔티티입니다.
         */
        Player savedPlayer =
                org.mockito.Mockito.mock(Player.class);

        when(member.getUserId())
                .thenReturn(userId);

        when(savedPlayer.getId())
                .thenReturn(playerId);

        /*
         * PUT 저장 요청은 사용자 행을 잠금 조회합니다.
         */
        when(memberRepository
                .findByEmailForUpdate(normalizedEmail))
                .thenReturn(Optional.of(member));

        when(userSettingRepository.findById(userId))
                .thenReturn(Optional.of(userSetting));

        /*
         * 이번 테스트에서는 관심팀을 선택하지 않습니다.
         */
        when(userFavoriteTeamRepository
                .findByMemberUserId(userId))
                .thenReturn(List.of());

        /*
         * playerId=208이 검색 캐시에 없는 상황입니다.
         */
        when(playerSearchMemoryCache
                .findPlayersByIds(List.of(playerId)))
                .thenReturn(Map.of());

        /*
         * 캐시에 없으므로 외부 API에서 정확한 ID로 조회합니다.
         */
        when(baseballDataSource
                .getPlayers(List.of(playerId)))
                .thenReturn(List.of(fetchedPlayer));

        /*
         * 외부에서 확인한 선수를 Writer가
         * 정상적으로 저장했다고 가정합니다.
         */
        when(playerRegistrationWriter.upsertPlayers(
                eq(List.of(fetchedPlayer)),
                any(Instant.class)
        )).thenReturn(List.of(savedPlayer));

        /*
         * 첫 조회는 기존 관심 선수,
         * 두 번째 조회는 저장 완료 후 최종 관심 선수입니다.
         */
        when(userFavoritePlayerRepository
                .findByMemberUserIdOrderByCreatedAtAsc(userId))
                .thenReturn(
                        List.of(),
                        List.of()
                );

        UserPreferenceUpdateRequest request =
                new UserPreferenceUpdateRequest();

        request.setSelectedTeamIds(List.of());
        request.setSelectedPlayerIds(
                List.of(playerId)
        );

        // when
        userPreferenceService.updateMyPreferences(
                requestEmail,
                request
        );

        // then
        /*
         * 먼저 검색 캐시를 확인합니다.
         */
        verify(playerSearchMemoryCache)
                .findPlayersByIds(
                        List.of(playerId)
                );

        /*
         * 캐시에 없었기 때문에 외부 선수 상세 조회가
         * 정확히 해당 ID로 실행돼야 합니다.
         */
        verify(baseballDataSource)
                .getPlayers(
                        List.of(playerId)
                );

        /*
         * 외부에서 확인된 선수만 Writer에 전달합니다.
         */
        verify(playerRegistrationWriter)
                .upsertPlayers(
                        eq(List.of(fetchedPlayer)),
                        any(Instant.class)
                );

        /*
         * 저장된 Player 엔티티를 사용해
         * 관심 선수 관계도 저장해야 합니다.
         */
        verify(userFavoritePlayerRepository)
                .saveAll(anyList());
    }


    /**
     * 검색 캐시에 없는 선수를 외부 API로 확인하는 과정에서
     * 장애가 발생하면 503용 예외로 변환하는지 검증합니다.
     *
     * 외부 조회가 실패한 상태에서는 선수 존재 여부를
     * 판단할 수 없으므로 다음 저장 작업은 실행하면 안 됩니다.
     *
     * - players 테이블 upsert 금지
     * - user_favorite_players 관계 저장 금지
     */
    @Test
    void updateMyPreferences_shouldThrowUnavailableWhenExternalLookupFails() {
        // given
        String requestEmail = "USER@EXAMPLE.COM";
        String normalizedEmail = "user@example.com";

        Long userId = 1L;
        Long playerId = 208L;

        Member member =
                org.mockito.Mockito.mock(Member.class);

        UserSetting userSetting =
                org.mockito.Mockito.mock(UserSetting.class);

        when(member.getUserId())
                .thenReturn(userId);

        /*
         * PUT 선호 설정 변경은 사용자 행을
         * 비관적 쓰기 잠금으로 조회합니다.
         */
        when(memberRepository
                .findByEmailForUpdate(normalizedEmail))
                .thenReturn(Optional.of(member));

        when(userSettingRepository.findById(userId))
                .thenReturn(Optional.of(userSetting));

        /*
         * 이번 테스트에서는 관심팀을 선택하지 않습니다.
         */
        when(userFavoriteTeamRepository
                .findByMemberUserId(userId))
                .thenReturn(List.of());

        /*
         * 검색 캐시에 선수 정보가 없으므로
         * 외부 정확 조회가 필요한 상황입니다.
         */
        when(playerSearchMemoryCache
                .findPlayersByIds(List.of(playerId)))
                .thenReturn(Map.of());

        /*
         * 외부 선수 API에서 네트워크 장애가 발생했다고 가정합니다.
         */
        when(baseballDataSource
                .getPlayers(List.of(playerId)))
                .thenThrow(
                        new RuntimeException(
                                "외부 선수 API 연결 실패"
                        )
                );

        UserPreferenceUpdateRequest request =
                new UserPreferenceUpdateRequest();

        request.setSelectedTeamIds(List.of());
        request.setSelectedPlayerIds(
                List.of(playerId)
        );

        // when
        PlayerLookupUnavailableException exception =
                assertThrows(
                        PlayerLookupUnavailableException.class,
                        () -> userPreferenceService
                                .updateMyPreferences(
                                        requestEmail,
                                        request
                                )
                );

        // then
        assertEquals(
                "선수 정보를 일시적으로 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.",
                exception.getMessage()
        );

        /*
         * 외부 API에서 받은 원래 예외가 cause로 보존돼야
         * 서버 로그에서 실제 장애 원인을 추적할 수 있습니다.
         */
        assertEquals(
                "외부 선수 API 연결 실패",
                exception.getCause().getMessage()
        );

        verify(playerSearchMemoryCache)
                .findPlayersByIds(
                        List.of(playerId)
                );

        verify(baseballDataSource)
                .getPlayers(
                        List.of(playerId)
                );

        /*
         * 외부 조회가 실패했으므로 선수 DB upsert를
         * 시도하면 안 됩니다.
         */
        verify(
                playerRegistrationWriter,
                never()
        ).upsertPlayers(
                anyList(),
                any(Instant.class)
        );

        /*
         * 검증되지 않은 선수로 관심 선수 관계도
         * 저장하면 안 됩니다.
         */
        verify(
                userFavoritePlayerRepository,
                never()
        ).saveAll(anyList());
    }


    /**
     * 외부 선수 API 호출은 정상적으로 완료됐지만
     * 요청한 선수 ID가 결과에 포함되지 않은 경우,
     * 잘못된 관심 선수 ID로 처리하는지 검증합니다.
     *
     * 외부 API 장애와의 차이:
     *
     * - API 호출 실패: PlayerLookupUnavailableException → 503
     * - API 호출 성공, 선수 없음: InvalidFavoritePlayerException → 400
     */
    @Test
    void updateMyPreferences_shouldRejectPlayerIdMissingFromExternalResult() {
        // given
        String requestEmail = "USER@EXAMPLE.COM";
        String normalizedEmail = "user@example.com";

        Long userId = 1L;

        /*
         * 실제 외부 선수 데이터에는 존재하지 않는다고 가정할 ID입니다.
         */
        Long invalidPlayerId = 999999L;

        Member member =
                org.mockito.Mockito.mock(Member.class);

        UserSetting userSetting =
                org.mockito.Mockito.mock(UserSetting.class);

        when(member.getUserId())
                .thenReturn(userId);

        /*
         * PUT 선호 설정 변경은 사용자 행을
         * 비관적 쓰기 잠금으로 조회합니다.
         */
        when(memberRepository
                .findByEmailForUpdate(normalizedEmail))
                .thenReturn(Optional.of(member));

        when(userSettingRepository.findById(userId))
                .thenReturn(Optional.of(userSetting));

        /*
         * 이번 테스트에서는 관심팀을 선택하지 않습니다.
         */
        when(userFavoriteTeamRepository
                .findByMemberUserId(userId))
                .thenReturn(List.of());

        /*
         * 검색 캐시에도 요청한 선수가 없습니다.
         */
        when(playerSearchMemoryCache
                .findPlayersByIds(
                        List.of(invalidPlayerId)
                ))
                .thenReturn(Map.of());

        /*
         * 외부 API 호출 자체는 성공했지만,
         * 해당 ID에 대응하는 선수가 없어서 빈 목록을 반환합니다.
         *
         * 이 경우는 외부 장애가 아니라 잘못된 선수 ID입니다.
         */
        when(baseballDataSource
                .getPlayers(
                        List.of(invalidPlayerId)
                ))
                .thenReturn(List.of());

        UserPreferenceUpdateRequest request =
                new UserPreferenceUpdateRequest();

        request.setSelectedTeamIds(List.of());
        request.setSelectedPlayerIds(
                List.of(invalidPlayerId)
        );

        // when
        InvalidFavoritePlayerException exception =
                assertThrows(
                        InvalidFavoritePlayerException.class,
                        () -> userPreferenceService
                                .updateMyPreferences(
                                        requestEmail,
                                        request
                                )
                );

        // then
        assertEquals(
                "존재하지 않는 관심 선수가 포함되어 있습니다.",
                exception.getMessage()
        );

        /*
         * 검색 캐시를 먼저 확인한 뒤,
         * 캐시에 없어서 외부 정확 조회까지 실행해야 합니다.
         */
        verify(playerSearchMemoryCache)
                .findPlayersByIds(
                        List.of(invalidPlayerId)
                );

        verify(baseballDataSource)
                .getPlayers(
                        List.of(invalidPlayerId)
                );

        /*
         * 외부 결과에서 선수가 확인되지 않았으므로
         * players 테이블 upsert는 실행하면 안 됩니다.
         */
        verify(
                playerRegistrationWriter,
                never()
        ).upsertPlayers(
                anyList(),
                any(Instant.class)
        );

        /*
         * 검증되지 않은 ID로 관심 선수 관계도
         * 저장하면 안 됩니다.
         */
        verify(
                userFavoritePlayerRepository,
                never()
        ).saveAll(anyList());
    }
}