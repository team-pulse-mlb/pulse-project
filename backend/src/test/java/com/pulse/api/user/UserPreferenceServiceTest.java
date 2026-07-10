package com.pulse.api.user;

import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import com.pulse.api.user.domain.UserFavoriteTeam;
import com.pulse.api.user.domain.UserFavoriteTeamRepository;
import com.pulse.api.user.domain.UserSetting;
import com.pulse.api.user.domain.UserSettingRepository;
import com.pulse.api.user.dto.UserPreferenceUpdateRequest;
import com.pulse.domain.Team;
import com.pulse.domain.TeamRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        when(memberRepository.findByEmail(normalizedEmail))
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
}