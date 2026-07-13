package com.pulse.api.notification.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * user_notifications 테이블에 접근하는 Repository입니다.
 *
 * 담당 기능:
 * 1. RabbitMQ 이벤트를 사용자별 알림으로 중복 없이 저장
 * 2. 현재 사용자의 최근 알림 목록 조회
 * 3. 선택한 알림 읽음 처리
 * 4. 현재 사용자의 모든 미읽음 알림 처리
 */
public interface UserNotificationRepository
        extends JpaRepository<UserNotification, Long>,
        UserNotificationInsertRepository {

    /**
     * 특정 사용자의 최근 알림을 최신순으로 조회합니다.
     *
     * 서비스에서 현재 시각 기준 7일 전을 cutoff로 전달하면
     * 알림 센터에는 최근 7일 알림만 노출됩니다.
     *
     * Member의 userId를 조건으로 사용하므로
     * 다른 사용자의 알림은 조회되지 않습니다.
     *
     * event 연관 관계도 함께 조회해서
     * 알림 타입과 gameId를 추가 쿼리 없이 사용할 수 있습니다.
     *
     * @param userId 현재 로그인한 사용자의 ID
     * @param cutoff 조회를 시작할 최소 생성 시각
     * @return 최신순 사용자 알림 목록
     */
    @EntityGraph(attributePaths = "event")
    List<UserNotification>
    findByMemberUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            Long userId,
            Instant cutoff
    );

    /**
     * 선택한 알림 ID들을 현재 사용자의 읽음 상태로 변경합니다.
     *
     * 반드시 userId 조건을 함께 사용합니다.
     *
     * notificationId만 조건으로 사용하면 다른 사용자의 알림도
     * 변경할 수 있는 보안 문제가 발생할 수 있습니다.
     *
     * 이미 읽은 알림은 readAt을 다시 변경하지 않습니다.
     *
     * @param userId 현재 로그인한 사용자 ID
     * @param notificationIds 읽음 처리할 알림 ID 목록
     * @param readAt 읽은 시각
     * @return 실제로 읽음 처리된 알림 개수
     */
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            update UserNotification notification
               set notification.readAt = :readAt
             where notification.member.userId = :userId
               and notification.id in :notificationIds
               and notification.readAt is null
            """)
    int markSelectedAsRead(
            @Param("userId") Long userId,
            @Param("notificationIds")
            Collection<Long> notificationIds,
            @Param("readAt") Instant readAt
    );

    /**
     * 현재 사용자의 모든 미읽음 알림을 읽음 처리합니다.
     *
     * 다른 사용자의 알림은 변경하지 않습니다.
     * 이미 읽은 알림의 기존 readAt도 유지합니다.
     *
     * @param userId 현재 로그인한 사용자의 ID
     * @param readAt 읽은 시각
     * @return 실제로 읽음 처리된 알림 개수
     */
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            update UserNotification notification
               set notification.readAt = :readAt
             where notification.member.userId = :userId
               and notification.readAt is null
            """)
    int markAllAsRead(
            @Param("userId") Long userId,
            @Param("readAt") Instant readAt
    );
}