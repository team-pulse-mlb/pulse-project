package com.pulse.api.user.dto;

import com.pulse.api.team.domain.Team;
import com.pulse.api.user.domain.SpoilerMode;
import com.pulse.api.user.domain.UserFavoriteTeam;
import com.pulse.api.user.domain.UserSetting;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class UserPreferenceResponse {

    /*
     * 로그인한 사용자의 관심팀 목록.
     */
    private List<FavoriteTeamResponse> favoriteTeams;

    /*
     * 로그인한 사용자의 알림 설정.
     */
    private NotificationSettingsResponse notificationSettings;

    /*
     * 스포일러 보호 모드.
     */
    private SpoilerMode spoilerMode;

    public static UserPreferenceResponse of(
            UserSetting userSetting,
            List<UserFavoriteTeam> favoriteTeams
    ) {
        return UserPreferenceResponse.builder()
                .favoriteTeams(
                        favoriteTeams.stream()
                                .map(FavoriteTeamResponse::from)
                                .toList()
                )
                .notificationSettings(
                        NotificationSettingsResponse.from(userSetting)
                )
                .spoilerMode(userSetting.getSpoilerMode())
                .build();
    }

    @Getter
    @Builder
    public static class FavoriteTeamResponse {

        private Long teamId;
        private Long logoTeamId;
        private String abbreviation;
        private String displayName;
        private String league;
        private String division;

        /*
         * 프론트에서 바로 img src로 사용할 로고 URL.
         *
         * DB에는 logo_url을 저장하지 않고,
         * teams.logo_team_id를 이용해 응답 DTO에서 조립한다.
         *
         * 예:
         * https://www.mlbstatic.com/team-logos/119.svg
         */
        private String logoUrl;

        public static FavoriteTeamResponse from(
                UserFavoriteTeam favoriteTeam
        ) {
            Team team = favoriteTeam.getTeam();

            return FavoriteTeamResponse.builder()
                    .teamId(team.getTeamId())
                    .logoTeamId(team.getLogoTeamId())
                    .abbreviation(team.getAbbreviation())
                    .displayName(team.getDisplayName())
                    .league(team.getLeague())
                    .division(team.getDivision())
                    .logoUrl(buildLogoUrl(team.getLogoTeamId()))
                    .build();
        }

        /*
         * MLB 공식 팀 로고 URL 생성 메서드.
         *
         * logoTeamId가 없으면 프론트에서 약어 표시 등으로 fallback 처리할 수 있게 null을 반환한다.
         */
        private static String buildLogoUrl(Long logoTeamId) {
            if (logoTeamId == null) {
                return null;
            }

            return "https://www.mlbstatic.com/team-logos/"
                    + logoTeamId
                    + ".svg";
        }
    }

    @Getter
    @Builder
    public static class NotificationSettingsResponse {

        private boolean all;
        private boolean gameStart;
        private boolean surge;
        private boolean gameSwitch;

        public static NotificationSettingsResponse from(
                UserSetting userSetting
        ) {
            boolean gameStart =
                    userSetting.isFavoriteTeamGameStartAlert();

            boolean surge =
                    userSetting.isImportantMomentAlert();

            boolean gameSwitch =
                    userSetting.isGameSwitchAlert();

            /*
             * all은 DB 컬럼이 아니라 프론트 UI용 값이다.
             *
             * 개별 알림 3개가 모두 true일 때만 all=true로 응답한다.
             */
            boolean all = gameStart && surge && gameSwitch;

            return NotificationSettingsResponse.builder()
                    .all(all)
                    .gameStart(gameStart)
                    .surge(surge)
                    .gameSwitch(gameSwitch)
                    .build();
        }
    }
}