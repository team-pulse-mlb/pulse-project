package com.pulse.api.user;

import com.pulse.api.user.domain.*;
import com.pulse.api.user.exception.FavoritePlayerLimitExceededException;
import com.pulse.api.user.exception.InvalidFavoritePlayerException;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.Team;
import com.pulse.domain.TeamRepository;
import com.pulse.api.user.dto.UserPreferenceResponse;
import com.pulse.api.user.dto.UserPreferenceUpdateRequest;
import com.pulse.api.user.exception.FavoriteTeamLimitExceededException;
import com.pulse.api.user.exception.InvalidFavoriteTeamException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
     * 사용자가 선택한 관심 선수 관계를 조회·저장·삭제하기 위한 Repository입니다.
     */
    private final UserFavoritePlayerRepository userFavoritePlayerRepository;

    /*
     * 요청으로 들어온 playerId가 실제 players 테이블에 존재하는지
     * 확인하기 위한 Repository입니다.
     */
    private final PlayerRepository playerRepository;


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

        List<UserFavoritePlayer> favoritePlayers =
                userFavoritePlayerRepository
                        .findByMemberUserIdOrderByCreatedAtAsc(
                                member.getUserId()
                        );

        return UserPreferenceResponse.of(
                userSetting,
                favoriteTeams,
                favoritePlayers
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
         * 기존 관심팀을 삭제합니다.
         */
        userFavoriteTeamRepository.deleteByMemberUserId(
                member.getUserId()
        );

        /*
         * 같은 트랜잭션 안에서 기존 관심팀과 일부 겹치는 팀을
         * 다시 INSERT할 수 있습니다.
         *
         * 예:
         * 기존 관심팀: [147, 119]
         * 새 관심팀:   [119, 110]
         *
         * DELETE SQL이 실제 DB에 반영되기 전에 INSERT가 실행되면
         * 아직 남아 있는 (user_id, 119)와 충돌할 수 있습니다.
         *
         * flush()를 호출해 기존 관심팀 DELETE를 먼저 DB에 반영한 후
         * 새로운 관심팀 INSERT를 진행하도록 순서를 보장합니다.
         */
        userFavoriteTeamRepository.flush();

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

        /*
         * selectedPlayerIds가 요청에 없으면 null입니다.
         *
         * null:
         * - 기존 관심 선수 설정 유지
         *
         * 빈 배열 []:
         * - 관심 선수 모두 해제
         *
         * 선수 ID 목록:
         * - 해당 목록으로 관심 선수 수정
         */
        List<UserFavoritePlayer> updatedFavoritePlayers =
                updateFavoritePlayersIfRequested(
                        member,
                        request.getSelectedPlayerIds()
                );

        return UserPreferenceResponse.of(
                userSetting,
                updatedFavoriteTeams,
                updatedFavoritePlayers
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


    /**
     * 관심 선수 수정 요청이 들어온 경우에만 관심 선수 목록을 변경합니다.
     *
     * selectedPlayerIds가 null이면:
     * - 프론트가 관심 선수 값을 보내지 않은 요청
     * - 기존 관심 선수 목록을 그대로 유지합니다.
     *
     * selectedPlayerIds가 빈 배열이면:
     * - 사용자가 관심 선수를 모두 해제한 요청
     * - 기존 관계를 모두 삭제합니다.
     */
    private List<UserFavoritePlayer> updateFavoritePlayersIfRequested(
            Member member,
            List<Long> requestedPlayerIds
    ) {
        /*
         * 기존 관심팀/알림 수정 요청에서는 selectedPlayerIds가 없을 수 있습니다.
         *
         * 이때 기존 관심 선수를 삭제하지 않고 그대로 반환합니다.
         */
        if (requestedPlayerIds == null) {
            return userFavoritePlayerRepository
                    .findByMemberUserIdOrderByCreatedAtAsc(
                            member.getUserId()
                    );
        }

        List<Long> selectedPlayerIds =
                normalizeAndValidateSelectedPlayerIds(
                        requestedPlayerIds
                );

        /*
         * 요청에 포함된 모든 선수가 players 테이블에 실제로 존재하는지 확인합니다.
         */
        List<Player> selectedPlayers =
                selectedPlayerIds.isEmpty()
                        ? List.of()
                        : playerRepository.findAllById(
                        selectedPlayerIds
                );

        if (selectedPlayers.size() != selectedPlayerIds.size()) {
            throw new InvalidFavoritePlayerException(
                    "존재하지 않는 관심 선수가 포함되어 있습니다."
            );
        }

        /*
         * playerId로 Player를 빠르게 찾을 수 있도록 Map으로 변환합니다.
         */
        Map<Long, Player> playerById =
                selectedPlayers.stream()
                        .collect(
                                Collectors.toMap(
                                        Player::getId,
                                        Function.identity()
                                )
                        );

        List<UserFavoritePlayer> existingFavorites =
                userFavoritePlayerRepository
                        .findByMemberUserIdOrderByCreatedAtAsc(
                                member.getUserId()
                        );

        Set<Long> selectedPlayerIdSet =
                new LinkedHashSet<>(selectedPlayerIds);

        /*
         * 기존에는 있었지만 새 요청에는 없는 선수 관계만 삭제합니다.
         *
         * 전체 삭제 후 전체 INSERT 방식이 아니라 차이만 반영하므로,
         * 복합 PK 충돌과 불필요한 DELETE/INSERT를 줄일 수 있습니다.
         */
        List<UserFavoritePlayer> favoritesToDelete =
                existingFavorites.stream()
                        .filter(favorite ->
                                !selectedPlayerIdSet.contains(
                                        favorite.getPlayer().getId()
                                )
                        )
                        .toList();

        if (!favoritesToDelete.isEmpty()) {
            userFavoritePlayerRepository.deleteAll(
                    favoritesToDelete
            );
        }

        Set<Long> existingPlayerIds =
                existingFavorites.stream()
                        .map(favorite ->
                                favorite.getPlayer().getId()
                        )
                        .collect(Collectors.toSet());

        /*
         * 새 요청에는 있지만 기존 관계에는 없었던 선수만 추가합니다.
         */
        List<UserFavoritePlayer> favoritesToSave =
                selectedPlayerIds.stream()
                        .filter(playerId ->
                                !existingPlayerIds.contains(playerId)
                        )
                        .map(playerId ->
                                UserFavoritePlayer.create(
                                        member,
                                        playerById.get(playerId)
                                )
                        )
                        .toList();

        if (!favoritesToSave.isEmpty()) {
            userFavoritePlayerRepository.saveAll(
                    favoritesToSave
            );
        }

        return userFavoritePlayerRepository
                .findByMemberUserIdOrderByCreatedAtAsc(
                        member.getUserId()
                );
    }


    /**
     * 관심 선수 ID 목록을 정리하고 검증합니다.
     *
     * 정책:
     * - 중복 선수 ID 제거
     * - 선택 순서 유지
     * - null 또는 0 이하 ID 거부
     * - 최대 5명
     */
    private List<Long> normalizeAndValidateSelectedPlayerIds(
            List<Long> selectedPlayerIds
    ) {
        if (selectedPlayerIds == null
                || selectedPlayerIds.isEmpty()) {
            return List.of();
        }

        Set<Long> uniquePlayerIds =
                new LinkedHashSet<>(selectedPlayerIds);

        boolean hasInvalidPlayerId =
                uniquePlayerIds.stream()
                        .anyMatch(playerId ->
                                playerId == null || playerId <= 0
                        );

        if (hasInvalidPlayerId) {
            throw new InvalidFavoritePlayerException(
                    "관심 선수 ID가 올바르지 않습니다."
            );
        }

        if (uniquePlayerIds.size() > 5) {
            throw new FavoritePlayerLimitExceededException(
                    "관심 선수는 최대 5명까지 선택할 수 있습니다."
            );
        }

        return new ArrayList<>(uniquePlayerIds);
    }
}