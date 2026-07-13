package com.pulse.common.user;

import java.util.Set;

/**
 * 회원 모듈이 제공하는 사용자 선호 조회 계약이다.
 */
public interface UserPreferenceReader {

    UserPreferences findByEmail(String email);

    record UserPreferences(Set<Long> favoriteTeamIds, Set<Long> favoritePlayerIds) {

        public UserPreferences {
            favoriteTeamIds = favoriteTeamIds == null ? Set.of() : Set.copyOf(favoriteTeamIds);
            favoritePlayerIds = favoritePlayerIds == null ? Set.of() : Set.copyOf(favoritePlayerIds);
        }

        public static UserPreferences empty() {
            return new UserPreferences(Set.of(), Set.of());
        }
    }
}
