package com.pulse.api.user.security.cookie;

import org.springframework.boot.context.properties.ConfigurationProperties;

/*
 * refreshToken 쿠키 옵션을 application.yml / 환경변수로 관리하기 위한 설정 클래스입니다.
 *
 * 왜 필요한가?
 * - 로컬 개발 환경은 HTTP이므로 secure=false가 필요합니다.
 * - 배포 환경은 HTTPS이므로 secure=true가 필요합니다.
 *
 * application.yml 매핑:
 *
 * app:
 *   cookie:
 *     refresh-token:
 *       secure: false
 *       same-site: Lax
 *       path: /api/members
 */
@ConfigurationProperties(prefix = "app.cookie.refresh-token")
public record RefreshTokenCookieProperties(

        /*
         * 쿠키의 Secure 옵션입니다.
         *
         * false:
         * - HTTP localhost 개발 환경에서 사용
         *
         * true:
         * - HTTPS 배포 환경에서 사용
         */
        boolean secure,

        /*
         * 쿠키의 SameSite 옵션입니다.
         *
         * Lax:
         * - 일반적인 로컬 개발/동일 사이트 흐름에 무난함
         *
         * None:
         * - 프론트와 백엔드가 다른 사이트로 배포되고,
         *   크로스사이트 쿠키 전송이 필요할 때 사용
         * - 단, SameSite=None은 Secure=true와 함께 써야 브라우저가 쿠키를 받아줍니다.
         */
        String sameSite,

        /*
         * refreshToken 쿠키가 전송될 경로입니다.
         *
         * /api/members로 제한하면 회원 인증 관련 API에만 쿠키가 전송됩니다.
         */
        String path
) {
}