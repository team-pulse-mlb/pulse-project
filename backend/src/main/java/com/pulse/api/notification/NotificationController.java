package com.pulse.api.notification;

import com.pulse.api.notification.dto.NotificationReadRequest;
import com.pulse.api.notification.dto.NotificationReadResponse;
import com.pulse.api.notification.dto.NotificationResponse;
import com.pulse.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 현재 로그인 사용자의 알림함 API를 제공하는 Controller입니다.
 *
 * 확정 API:
 *
 * GET /api/me/notifications
 * - 최근 7일 알림 목록 조회
 *
 * POST /api/me/notifications/read
 * - 선택 알림 또는 전체 알림 읽음 처리
 *
 * SecurityConfig에서 /api/me/** 경로는
 * 인증된 사용자만 접근할 수 있도록 설정되어 있습니다.
 */
@RestController
@RequestMapping("/api/me/notifications")
@RequiredArgsConstructor
@Tag(name = "알림", description = "현재 사용자의 알림 목록과 읽음 상태")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationController {

    /**
     * 알림함 조회와 읽음 처리를 담당하는 서비스입니다.
     */
    private final NotificationService notificationService;

    /**
     * 현재 로그인 사용자의 최근 7일 알림을 조회합니다.
     *
     * Authentication.getName()에는 JWT 인증 필터가 등록한
     * 현재 사용자의 이메일이 들어 있습니다.
     *
     * @param authentication 현재 로그인한 사용자의 인증 정보
     * @return 최신순 알림 목록
     */
    @Operation(summary = "내 알림 목록 조회", description = "최근 7일 알림을 최신순으로 반환한다.")
    @GetMapping
    public List<NotificationResponse> getMyNotifications(
            Authentication authentication
    ) {
        return notificationService.getMyNotifications(
                authentication.getName()
        );
    }

    /**
     * 현재 사용자의 알림을 읽음 처리합니다.
     *
     * request.all()이 true:
     * - 현재 사용자의 모든 미읽음 알림 처리
     *
     * request.all()이 false:
     * - notificationIds에 포함된 알림만 읽음 처리
     *
     * Repository 쿼리에 로그인 사용자 ID 조건이 포함되어 있으므로
     * 다른 사용자의 알림은 변경되지 않습니다.
     *
     * @param authentication 현재 로그인 사용자의 인증 정보
     * @param request 읽음 처리 요청
     * @return 실제로 읽음 처리된 개수
     */
    @Operation(
            summary = "내 알림 읽음 처리",
            description = "all=true이면 모든 미읽음 알림을, false이면 notificationIds의 알림만 처리한다."
    )
    @PostMapping("/read")
    public NotificationReadResponse markAsRead(
            Authentication authentication,
            @Valid @RequestBody NotificationReadRequest request
    ) {
        int updatedCount;

        /*
         * all=true이면 알림 ID 목록과 관계없이
         * 현재 사용자의 모든 미읽음 알림을 처리합니다.
         */
        if (request.all()) {
            updatedCount = notificationService.markAllAsRead(
                    authentication.getName()
            );
        } else {
            /*
             * all=false이면 사용자가 전달한 알림 ID만 처리합니다.
             *
             * notificationIds가 null 또는 빈 목록인 경우에는
             * Service에서 Repository를 호출하지 않고 0을 반환합니다.
             */
            updatedCount = notificationService.markSelectedAsRead(
                    authentication.getName(),
                    request.notificationIds()
            );
        }

        return new NotificationReadResponse(
                updatedCount
        );
    }
}
