package com.pulse.api.user;

import com.pulse.domain.Team;
import com.pulse.domain.TeamRepository;
import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import com.pulse.api.user.domain.UserFavoriteTeam;
import com.pulse.api.user.domain.UserFavoriteTeamRepository;
import com.pulse.api.user.domain.UserSetting;
import com.pulse.api.user.domain.UserSettingRepository;
import com.pulse.api.user.dto.UserPreferenceResponse;
import com.pulse.api.user.dto.UserPreferenceUpdateRequest;
import com.pulse.api.user.exception.FavoriteTeamLimitExceededException;
import com.pulse.api.user.exception.InvalidFavoriteTeamException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPreferenceService {

    /*
     * 로그인한 사용자의 Member 정보를 조회하기 위한 Repository.
     *
     * 현재 Spring Security의 principal에는 email이 username으로 들어가 있으므로,
     * Authentication.getName() 값으로 Member를 다시 조회한다.
     */
    private final MemberRepository memberRepository;

    /*
     * 사용자의 알림/스포일러 설정 조회·수정 Repository.
     */
    private final UserSettingRepository userSettingRepository;

    /*
     * 사용자의 관심팀 조회·삭제·저장 Repository.
     */
    private final UserFavoriteTeamRepository userFavoriteTeamRepository;

    /*
     * 요청으로 들어온 teamId가 실제 teams 테이블에 존재하는지 확인하기 위한 Repository.
     */
    private final TeamRepository teamRepository;

    /*
     * 로그인한 사용자의 관심팀 + 알림 설정 + 스포일러 모드를 조회한다.
     */
    public UserPreferenceResponse getMyPreferences(String email) {
        Member member = findMemberByEmail(email);

        UserSetting userSetting = userSettingRepository
                .findById(member.getUserId())
                .orElseGet(() -> UserSetting.createDefault(member));

        List<UserFavoriteTeam> favoriteTeams =
                userFavoriteTeamRepository.findByMemberUserId(
                        member.getUserId()
                );

        return UserPreferenceResponse.of(
                userSetting,
                favoriteTeams
        );
    }

    /*
     * 로그인한 사용자의 관심팀 + 알림 설정 + 스포일러 모드를 수정한다.
     *
     * 관심팀은 부분 수정이 아니라 전체 교체 방식으로 처리한다.
     */
    @Transactional
    public UserPreferenceResponse updateMyPreferences(
            String email,
            UserPreferenceUpdateRequest request
    ) {
        Member member = findMemberByEmail(email);

        UserSetting userSetting = userSettingRepository
                .findById(member.getUserId())
                .orElseGet(() -> userSettingRepository.save(
                        UserSetting.createDefault(member)
                ));

        UserPreferenceUpdateRequest.NotificationSettingsRequest notificationSettings =
                request.getNotificationSettings();

        if (notificationSettings == null) {
            notificationSettings =
                    new UserPreferenceUpdateRequest.NotificationSettingsRequest();
        }

        userSetting.updatePreferences(
                notificationSettings.isGameStart(),
                notificationSettings.isSurge(),
                notificationSettings.isGameSwitch()
        );

        List<Long> selectedTeamIds =
                normalizeAndValidateSelectedTeamIds(
                        request.getSelectedTeamIds()
                );

        /*
         * 기존 관심팀을 모두 삭제한 뒤,
         * 요청으로 들어온 관심팀 목록을 새로 저장한다.
         */
        userFavoriteTeamRepository.deleteByMemberUserId(
                member.getUserId()
        );

        if (!selectedTeamIds.isEmpty()) {
            List<Team> teams = teamRepository.findAllById(
                    selectedTeamIds
            );

            if (teams.size() != selectedTeamIds.size()) {
                throw new InvalidFavoriteTeamException(
                        "존재하지 않는 관심팀이 포함되어 있습니다."
                );
            }

            List<UserFavoriteTeam> favoriteTeams = teams.stream()
                    .map(team -> UserFavoriteTeam.create(member, team))
                    .toList();

            userFavoriteTeamRepository.saveAll(favoriteTeams);
        }

        List<UserFavoriteTeam> updatedFavoriteTeams =
                userFavoriteTeamRepository.findByMemberUserId(
                        member.getUserId()
                );

        return UserPreferenceResponse.of(
                userSetting,
                updatedFavoriteTeams
        );
    }

    /*
     * 현재 로그인한 사용자의 이메일로 Member를 조회한다.
     */
    private Member findMemberByEmail(String email) {
        String normalizedEmail = email
                .trim()
                .toLowerCase(Locale.ROOT);

        return memberRepository.findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "가입되지 않은 이메일입니다."
                        )
                );
    }

    /*
     * 관심팀 ID 목록을 정리하고 검증한다.
     *
     * 정책:
     * - null이면 빈 목록으로 처리
     * - 중복 제거
     * - 선택 순서 유지
     * - 0 이하 ID 차단
     * - 최대 3개 제한
     */
    private List<Long> normalizeAndValidateSelectedTeamIds(
            List<Long> selectedTeamIds
    ) {
        if (selectedTeamIds == null || selectedTeamIds.isEmpty()) {
            return List.of();
        }

        Set<Long> uniqueTeamIds = new LinkedHashSet<>(
                selectedTeamIds
        );

        boolean hasInvalidTeamId = uniqueTeamIds.stream()
                .anyMatch(teamId -> teamId == null || teamId <= 0);

        if (hasInvalidTeamId) {
            throw new InvalidFavoriteTeamException(
                    "관심팀 ID가 올바르지 않습니다."
            );
        }

        if (uniqueTeamIds.size() > 3) {
            throw new FavoriteTeamLimitExceededException(
                    "관심팀은 최대 3개까지 선택할 수 있습니다."
            );
        }

        return new ArrayList<>(uniqueTeamIds);
    }
}