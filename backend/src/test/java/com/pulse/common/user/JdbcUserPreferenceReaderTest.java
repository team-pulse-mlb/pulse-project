package com.pulse.common.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcUserPreferenceReaderTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JdbcUserPreferenceReader reader = new JdbcUserPreferenceReader(jdbcTemplate);

    @Test
    void findByEmail_shouldReadTeamAndPlayerPreferencesWithNormalizedEmail() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("user@example.com")))
                .thenReturn(List.of(147L, 119L), List.of(660271L));

        UserPreferenceReader.UserPreferences result = reader.findByEmail(" User@Example.com ");

        assertThat(result.favoriteTeamIds()).containsExactlyInAnyOrder(147L, 119L);
        assertThat(result.favoritePlayerIds()).containsExactly(660271L);
        verify(jdbcTemplate, times(2))
                .queryForList(anyString(), eq(Long.class), eq("user@example.com"));
    }

    @Test
    void findByEmail_shouldReturnEmptyPreferencesForBlankEmail() {
        UserPreferenceReader.UserPreferences result = reader.findByEmail(" ");

        assertThat(result).isEqualTo(UserPreferenceReader.UserPreferences.empty());
        verify(jdbcTemplate, never()).queryForList(anyString(), eq(Long.class), anyString());
    }
}
