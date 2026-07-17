package com.pulse.api.home;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class HomeRankingControllerTest {

    @Test
    @DisplayName("익명 인증 토큰은 익명 랭킹 캐시 경로를 사용한다")
    void live_shouldTreatAnonymousAuthenticationAsAnonymous() {
        HomeQueryService service = mock(HomeQueryService.class);
        HomeRankingController controller = new HomeRankingController(service);
        AnonymousAuthenticationToken authentication = new AnonymousAuthenticationToken(
                "anonymous", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        controller.live(5, authentication);

        verify(service).getRanking(5, null);
    }
}
