package com.pulse.api.sse;

import com.pulse.api.sse.dto.SseTokenResponse;
import com.pulse.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.OptionalLong;

/**
 * SSE 연결과 인증용 1회용 토큰 발급을 담당하는 Controller입니다.
 *
 * 비로그인 연결:
 *
 * GET /api/sse
 *
 * - ranking_changed
 * - game_updated
 *
 * 로그인 연결:
 *
 * 1. POST /api/sse/token
 * 2. GET /api/sse?token={발급된 토큰}
 *
 * - ranking_changed
 * - game_updated
 * - notification_created
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnWebApplication(
        type = ConditionalOnWebApplication.Type.SERVLET
)
@ConditionalOnProperty(
        prefix = "pulse.sse",
        name = "enabled",
        havingValue = "true"
)
@Tag(name = "실시간 이벤트", description = "REST 재조회 신호를 전달하는 SSE 연결")
public class SseController {

    /**
     * 비로그인 및 로그인 사용자별 SSE 연결을 관리합니다.
     */
    private final SseEmitterRegistry emitterRegistry;

    /**
     * Redis 기반 SSE 1회용 토큰을 발급하고 소모합니다.
     */
    private final SseTokenService sseTokenService;

    /**
     * 현재 로그인 사용자에게 SSE 연결용 1회용 토큰을 발급합니다.
     *
     * 이 요청에는 JWT Authorization 헤더가 필요합니다.
     *
     * 처리 순서:
     *
     * 1. Authentication에서 로그인 이메일 확인
     * 2. 이메일로 Member 조회
     * 3. Redis에 사용자 ID를 TTL 60초로 저장
     * 4. 프론트에 1회용 토큰 반환
     *
     * @param authentication 현재 로그인 사용자의 인증 정보
     * @return SSE 연결용 1회용 토큰
     */
    @Operation(
            summary = "SSE 1회용 토큰 발급",
            description = "로그인 SSE 연결에 사용할 유효기간 60초의 1회용 토큰을 발급한다.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @PostMapping("/api/sse/token")
    public SseTokenResponse issueToken(
            Authentication authentication
    ) {
        String token =
                sseTokenService.issue(
                        authentication.getName()
                );

        return new SseTokenResponse(token);
    }

    /**
     * SSE 연결을 생성합니다.
     *
     * token이 없는 경우:
     * - 비로그인 공개 연결
     *
     * token이 있는 경우:
     * - Redis에서 1회용 토큰을 소모
     * - 토큰에 저장된 userId로 로그인 사용자 연결 등록
     *
     * 유효하지 않은 token이 명시적으로 전달되면
     * 비로그인 연결로 낮추지 않고 401을 반환합니다.
     *
     * 잘못된 토큰을 익명 연결로 처리하면,
     * 프론트가 인증 연결에 성공했다고 잘못 판단할 수 있기 때문입니다.
     *
     * @param token 선택적으로 전달되는 SSE 1회용 토큰
     * @return SSE 스트림 또는 401 응답
     */
    @Operation(
            summary = "SSE 구독",
            description = """
                    token이 없으면 ranking_changed와 game_updated를 받는 공개 연결을 생성한다.
                    유효한 1회용 token이 있으면 notification_created도 받는 인증 연결을 생성한다.
                    이벤트 payload는 재조회 신호만 포함하며 연결·재연결 정책은 API_CONTRACTS.md를 따른다.
                    """
    )
    @GetMapping(
            value = "/api/sse",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<SseEmitter> subscribe(
            @Parameter(description = "로그인 연결용 1회용 토큰")
            @RequestParam(
                    required = false
            )
            String token
    ) {
        /*
         * 토큰이 없거나 빈 문자열이면
         * 기존과 동일하게 비로그인 SSE 연결을 생성합니다.
         */
        if (token == null || token.isBlank()) {
            return createSseResponse(
                    emitterRegistry.subscribe()
            );
        }

        /*
         * getAndDelete()로 토큰을 한 번만 소모합니다.
         *
         * 유효하거나 처음 사용한 토큰:
         * OptionalLong.of(userId)
         *
         * 만료·재사용·존재하지 않는 토큰:
         * OptionalLong.empty()
         */
        OptionalLong userId =
                sseTokenService.consumeUserId(token);

        if (userId.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .build();
        }

        /*
         * 로그인 사용자 연결은 userId별로 등록되므로
         * 해당 사용자의 notification_created를 받을 수 있습니다.
         */
        return createSseResponse(
                emitterRegistry.subscribe(
                        userId.getAsLong()
                )
        );
    }

    /**
     * 공개 연결과 인증 연결이 사용하는
     * 공통 SSE 응답 헤더를 만듭니다.
     */
    private ResponseEntity<SseEmitter> createSseResponse(
            SseEmitter emitter
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())

                /*
                 * nginx 리버스 프록시가 SSE 이벤트를 모았다가
                 * 한 번에 전달하지 않도록 버퍼링을 비활성화합니다.
                 */
                .header(
                        "X-Accel-Buffering",
                        "no"
                )
                .body(emitter);
    }
}
