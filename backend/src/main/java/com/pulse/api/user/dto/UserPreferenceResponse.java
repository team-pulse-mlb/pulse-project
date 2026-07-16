package com.pulse.api.user.dto;

import com.pulse.domain.Team;
import com.pulse.api.user.domain.UserFavoriteTeam;
import com.pulse.api.user.domain.UserSetting;
import com.pulse.api.user.domain.UserFavoritePlayer;
import com.pulse.domain.Player;
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
     * 로그인한 사용자가 선택한 관심 선수 목록입니다.
     */
    private List<FavoritePlayerResponse> favoritePlayers;

    /*
     * 로그인한 사용자의 알림 설정.
     */
    private NotificationSettingsResponse notificationSettings;

    /**
     * 기존 호출 코드와의 호환성을 유지하기 위한 메서드입니다.
     *
     * 아직 관심 선수 조회가 연결되지 않은 호출에서는
     * favoritePlayers를 빈 목록으로 응답합니다.
     */
    public static UserPreferenceResponse of(
            UserSetting userSetting,
            List<UserFavoriteTeam> favoriteTeams
    ) {
        return of(
                userSetting,
                favoriteTeams,
                List.of()
        );
    }

    /**
     * 관심팀, 관심 선수, 알림 설정을 모두 포함한 응답을 생성합니다.
     */
    public static UserPreferenceResponse of(
            UserSetting userSetting,
            List<UserFavoriteTeam> favoriteTeams,
            List<UserFavoritePlayer> favoritePlayers
    ) {
        return UserPreferenceResponse.builder()
                .favoriteTeams(
                        favoriteTeams == null
                                ? List.of()
                                : favoriteTeams.stream()
                                .map(FavoriteTeamResponse::from)
                                .toList()
                )
                .favoritePlayers(
                        favoritePlayers == null
                                ? List.of()
                                : favoritePlayers.stream()
                                .map(FavoritePlayerResponse::from)
                                .toList()
                )
                .notificationSettings(
                        NotificationSettingsResponse.from(
                                userSetting
                        )
                )
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

    /**
     * 마이페이지에 표시할 관심 선수 응답입니다.
     */
    @Getter
    @Builder
    public static class FavoritePlayerResponse {

        /*
         * balldontlie 선수 ID입니다.
         */
        private Long playerId;

        /*
         * 선수 전체 영문 이름입니다.
         */
        private String fullName;

        /*
         * 선수 포지션입니다.
         *
         * 예:
         * DH, P, SS
         */
        private String position;

        /*
         * 선수의 현재 소속팀 ID입니다.
         *
         * 팀 정보가 없으면 null일 수 있습니다.
         */
        private Long teamId;

        /**
         * 관심 선수 엔티티에서 프론트 응답을 생성합니다.
         */
        public static FavoritePlayerResponse from(
                UserFavoritePlayer favoritePlayer
        ) {
            Player player = favoritePlayer.getPlayer();

            return FavoritePlayerResponse.builder()
                    .playerId(player.getId())
                    .fullName(player.getFullName())
                    .position(player.getPosition())
                    .teamId(player.getTeamId())
                    .build();
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