package com.pulse.common.user;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 회원 모듈이 저장한 관심 팀·선수를 홈 개인화 계약으로 변환한다.
 * 팀원 모듈의 엔티티에 직접 의존하지 않고 확정 스키마를 읽기 전용으로 조회한다.
 */
@Component
@RequiredArgsConstructor
public class JdbcUserPreferenceReader implements UserPreferenceReader {

    private static final String FAVORITE_TEAM_IDS_SQL = """
            SELECT favorite.team_id
            FROM user_favorite_teams favorite
            JOIN users member ON member.user_id = favorite.user_id
            WHERE member.email = ?
              AND member.status = 'ACTIVE'
            """;

    private static final String FAVORITE_PLAYER_IDS_SQL = """
            SELECT favorite.player_id
            FROM user_favorite_players favorite
            JOIN users member ON member.user_id = favorite.user_id
            WHERE member.email = ?
              AND member.status = 'ACTIVE'
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public UserPreferences findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return UserPreferences.empty();
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Set<Long> favoriteTeamIds = queryIds(FAVORITE_TEAM_IDS_SQL, normalizedEmail);
        Set<Long> favoritePlayerIds = queryIds(FAVORITE_PLAYER_IDS_SQL, normalizedEmail);
        return new UserPreferences(favoriteTeamIds, favoritePlayerIds);
    }

    private Set<Long> queryIds(String sql, String email) {
        List<Long> ids = jdbcTemplate.queryForList(sql, Long.class, email);
        return Set.copyOf(new LinkedHashSet<>(ids));
    }
}
