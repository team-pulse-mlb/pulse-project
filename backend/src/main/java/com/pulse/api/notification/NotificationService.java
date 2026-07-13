package com.pulse.api.notification;

import com.pulse.api.notification.domain.UserNotification;
import com.pulse.api.notification.domain.UserNotificationRepository;
import com.pulse.api.notification.dto.NotificationResponse;
import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 현재 로그인 사용자의 알림함을 조회하고
 * 알림을 읽음 상태로 변경하는 서비스입니다.
 *
 * 이 서비스는 알림의 "조회 영역"을 담당합니다.
 *
 * 알림 생성 과정:
 * RabbitMQ 이벤트
 * → NotificationEventListener
 * → NotificationFanOutService
 * → user_notifications 저장
 *
 * 알림 조회 과정:
 * NotificationController
 * → NotificationService
 * → UserNotificationRepository
 * → NotificationResponse 변환
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    /**
     * 로그인 사용자의 회원 정보를 조회하기 위한 Repository입니다.
     *
     * Spring Security의 Authentication.getName()에는
     * 현재 로그인 사용자의 이메일이 들어 있으므로,
     * 해당 이메일로 Member를 조회합니다.
     */
    private final MemberRepository memberRepository;

    /**
     * 사용자별 알림 조회 및 읽음 처리를 담당하는 Repository입니다.
     */
    private final UserNotificationRepository userNotificationRepository;

    /**
     * 알림함에서 노출할 기간입니다.
     *
     * 현재 정책은 최근 7일입니다.
     *
     * 여기서 7일은 우선 "조회 범위"를 의미합니다.
     * 즉, 7일보다 오래된 데이터가 DB에 남아 있더라도
     * 알림함 화면에는 노출되지 않습니다.
     *
     * 실제 DB 데이터 삭제 작업은 추후 정리 스케줄러에서
     * 별도로 구현할 수 있습니다.
     */
    private static final long NOTIFICATION_RETENTION_DAYS = 7L;

    /**
     * 현재 로그인한 사용자의 최근 7일 알림을 조회합니다.
     *
     * 처리 과정:
     * 1. 이메일로 Member 조회
     * 2. 현재 시각 기준 7일 전 시각 계산
     * 3. 해당 사용자의 최근 알림을 최신순으로 조회
     * 4. UserNotification 엔티티를 NotificationResponse DTO로 변환
     *
     * @param email 현재 로그인한 사용자의 이메일
     * @return 최근 7일 알림 목록
     */
    public List<NotificationResponse> getMyNotifications(
            String email
    ) {
        Member member = findMemberByEmail(email);

        /*
         * 예:
         * 현재 시각: 2026-07-13 15:00
         * cutoff:   2026-07-06 15:00
         *
         * createdAt이 cutoff 이상인 알림만 조회합니다.
         */
        Instant cutoff = Instant.now()
                .minus(
                        NOTIFICATION_RETENTION_DAYS,
                        ChronoUnit.DAYS
                );

        List<UserNotification> notifications =
                userNotificationRepository
                        .findByMemberUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                                member.getUserId(),
                                cutoff
                        );

        /*
         * JPA 엔티티를 Controller에서 직접 반환하지 않고
         * 프론트 전용 응답 DTO로 변환합니다.
         */
        return notifications.stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /**
     * 사용자가 선택한 알림들을 읽음 처리합니다.
     *
     * Repository 쿼리에 userId 조건이 포함되어 있으므로,
     * 다른 사용자의 notificationId가 요청에 포함되더라도
     * 해당 알림은 수정되지 않습니다.
     *
     * notificationIds가 비어 있을 때 Repository를 호출하지 않는 이유:
     *
     * JPQL의 IN 조건에 빈 목록이 전달되면 DB 또는 JPA 구현에 따라
     * 잘못된 SQL이 만들어질 가능성이 있기 때문입니다.
     *
     * @param email 현재 로그인한 사용자의 이메일
     * @param notificationIds 읽음 처리할 알림 ID 목록
     * @return 실제로 읽음 처리된 알림 개수
     */
    @Transactional
    public int markSelectedAsRead(
            String email,
            Collection<Long> notificationIds
    ) {
        /*
         * 요청 자체가 null이거나 빈 목록이면
         * 변경할 알림이 없으므로 바로 0을 반환합니다.
         */
        if (notificationIds == null || notificationIds.isEmpty()) {
            return 0;
        }

        /*
         * null ID는 제거하고 중복 ID도 제거합니다.
         *
         * 예:
         * [1, 1, null, 2]
         * → [1, 2]
         */
        List<Long> normalizedNotificationIds =
                notificationIds.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        if (normalizedNotificationIds.isEmpty()) {
            return 0;
        }

        Member member = findMemberByEmail(email);

        return userNotificationRepository.markSelectedAsRead(
                member.getUserId(),
                normalizedNotificationIds,
                Instant.now()
        );
    }

    /**
     * 현재 로그인한 사용자의 모든 미읽음 알림을 읽음 처리합니다.
     *
     * 이미 읽은 알림은 Repository의 readAt is null 조건 때문에
     * 최초 읽음 시각이 그대로 유지됩니다.
     *
     * @param email 현재 로그인한 사용자의 이메일
     * @return 실제로 읽음 처리된 알림 개수
     */
    @Transactional
    public int markAllAsRead(
            String email
    ) {
        Member member = findMemberByEmail(email);

        return userNotificationRepository.markAllAsRead(
                member.getUserId(),
                Instant.now()
        );
    }

    /**
     * 로그인 사용자 이메일로 Member를 조회합니다.
     *
     * 회원가입과 로그인에서 이메일을 소문자로 정규화하므로
     * 조회 시에도 동일하게 공백 제거와 소문자 변환을 적용합니다.
     *
     * @param email 현재 인증 정보에 들어 있는 이메일
     * @return 조회된 회원
     */
    private Member findMemberByEmail(
            String email
    ) {
        String normalizedEmail = email
                .trim()
                .toLowerCase(Locale.ROOT);

        return memberRepository.findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "가입되지 않은 이메일입니다."
                        )
                );
    }
}