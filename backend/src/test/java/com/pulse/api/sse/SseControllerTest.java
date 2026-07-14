package com.pulse.api.sse;

import com.pulse.api.sse.dto.SseTokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * SseController 단위 테스트입니다.
 *
 * 실제 Redis나 HTTP 서버를 실행하지 않고 다음 흐름을 검증합니다.
 *
 * 1. 로그인 사용자의 1회용 토큰 발급
 * 2. 토큰 없는 비로그인 SSE 연결
 * 3. 유효한 토큰을 이용한 로그인 SSE 연결
 * 4. 만료되거나 재사용된 토큰의 연결 거부
 */
@ExtendWith(MockitoExtension.class)
class SseControllerTest {

    @Mock
    private SseEmitterRegistry emitterRegistry;

    @Mock
    private SseTokenService sseTokenService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private SseController sseController;

    /**
     * 로그인 사용자의 이메일로 SSE 토큰을 발급하는지 검증합니다.
     */
    @Test
    void issueToken_shouldReturnOneTimeToken() {
        // given
        when(authentication.getName())
                .thenReturn("user@example.com");

        when(sseTokenService.issue(
                "user@example.com"
        )).thenReturn(
                "issued-token"
        );

        // when
        SseTokenResponse response =
                sseController.issueToken(
                        authentication
                );

        // then
        assertThat(response.token())
                .isEqualTo("issued-token");

        verify(sseTokenService)
                .issue("user@example.com");
    }

    /**
     * token을 전달하지 않으면 비로그인 SSE 연결을 생성하는지 검증합니다.
     */
    @Test
    void subscribe_shouldCreateAnonymousConnectionWithoutToken() {
        // given
        SseEmitter emitter =
                new SseEmitter();

        when(emitterRegistry.subscribe())
                .thenReturn(emitter);

        // when
        ResponseEntity<SseEmitter> response =
                sseController.subscribe(null);

        // then
        assertThat(response.getStatusCode().value())
                .isEqualTo(200);

        assertThat(response.getBody())
                .isSameAs(emitter);

        assertThat(
                response.getHeaders().getCacheControl()
        ).isEqualTo("no-store");

        assertThat(
                response.getHeaders()
                        .getFirst("X-Accel-Buffering")
        ).isEqualTo("no");

        verify(emitterRegistry)
                .subscribe();

        /*
         * 토큰이 없으므로 Redis 토큰 조회는 하지 않습니다.
         */
        verifyNoInteractions(sseTokenService);
    }

    /**
     * 유효한 토큰이면 토큰에 저장된 사용자 ID로
     * 인증 SSE 연결을 생성하는지 검증합니다.
     */
    @Test
    void subscribe_shouldCreateAuthenticatedConnectionWithValidToken() {
        // given
        SseEmitter emitter =
                new SseEmitter();

        when(sseTokenService.consumeUserId(
                "valid-token"
        )).thenReturn(
                OptionalLong.of(7L)
        );

        when(emitterRegistry.subscribe(7L))
                .thenReturn(emitter);

        // when
        ResponseEntity<SseEmitter> response =
                sseController.subscribe(
                        "valid-token"
                );

        // then
        assertThat(response.getStatusCode().value())
                .isEqualTo(200);

        assertThat(response.getBody())
                .isSameAs(emitter);

        assertThat(
                response.getHeaders()
                        .getFirst("X-Accel-Buffering")
        ).isEqualTo("no");

        verify(sseTokenService)
                .consumeUserId("valid-token");

        verify(emitterRegistry)
                .subscribe(7L);

        /*
         * 로그인 연결에서는 비로그인 subscribe가
         * 호출되면 안 됩니다.
         */
        verify(emitterRegistry, never())
                .subscribe();
    }

    /**
     * 만료·재사용·존재하지 않는 토큰은
     * 인증 SSE 연결을 생성하지 않고 401을 반환해야 합니다.
     */
    @Test
    void subscribe_shouldRejectInvalidToken() {
        // given
        when(sseTokenService.consumeUserId(
                "invalid-token"
        )).thenReturn(
                OptionalLong.empty()
        );

        // when
        ResponseEntity<SseEmitter> response =
                sseController.subscribe(
                        "invalid-token"
                );

        // then
        assertThat(response.getStatusCode().value())
                .isEqualTo(401);

        assertThat(response.getBody())
                .isNull();

        /*
         * 유효한 사용자 ID가 없으므로
         * 어떤 SSE 연결도 생성하지 않아야 합니다.
         */
        verifyNoInteractions(emitterRegistry);
    }
}