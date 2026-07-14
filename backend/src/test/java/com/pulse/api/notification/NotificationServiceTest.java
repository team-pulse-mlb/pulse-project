package com.pulse.api.notification;

import com.pulse.api.notification.domain.UserNotification;
import com.pulse.api.notification.domain.UserNotificationRepository;
import com.pulse.api.notification.dto.NotificationResponse;
import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import com.pulse.domain.NotificationEventLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * NotificationService의 단위 테스트입니다.
 *
 * 실제 DB를 실행하지 않고 MemberRepository와
 * UserNotificationRepository를 Mockito 가짜 객체로 대체합니다.
 *
 * 테스트 대상:
 * 1. 최근 알림 조회
 * 2. 선택한 알림 읽음 처리
 * 3. 빈 알림 ID 요청 처리
 * 4. 모든 알림 읽음 처리
 * 5. 존재하지 않는 사용자 처리
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    /**
     * 이메일로 회원을 조회하는 Repository의 가짜 객체입니다.
     */
    @Mock
    private MemberRepository memberRepository;

    /**
     * 알림 조회 및 읽음 처리를 담당하는 Repository의 가짜 객체입니다.
     */
    @Mock
    private UserNotificationRepository userNotificationRepository;

    /**
     * 위 Mock 객체들을 생성자로 주입받은 실제 테스트 대상입니다.
     */
    @InjectMocks
    private NotificationService notificationService;

    /**
     * 현재 로그인 사용자의 최근 알림을 조회하면
     * NotificationResponse DTO로 변환되는지 확인합니다.
     */
    @Test
    void getMyNotificationsReturnsRecentNotifications() {
        // given
        Member member = mock(Member.class);
        UserNotification notification = mock(UserNotification.class);
        NotificationEventLog event = mock(NotificationEventLog.class);

        /*
         * 로그인 이메일 앞뒤에 공백이 있고 대문자가 포함돼도
         * 서비스에서 소문자로 정규화한 뒤 조회해야 합니다.
         */
        when(memberRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(member));

        when(member.getUserId())
                .thenReturn(7L);

        /*
         * Repository가 최근 사용자 알림 한 건을 반환한다고 가정합니다.
         */
        when(userNotificationRepository
                .findByMemberUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(7L),
                        any(Instant.class)
                ))
                .thenReturn(List.of(notification));

        /*
         * NotificationResponse.from()에서 사용하는 엔티티 값을 설정합니다.
         */
        Instant createdAt = Instant.parse(
                "2026-07-13T03:00:00Z"
        );

        when(notification.getId())
                .thenReturn(100L);

        when(notification.getEvent())
                .thenReturn(event);

        when(notification.getMessage())
                .thenReturn("경기 흐름이 급변하고 있어요.");

        when(notification.isRead())
                .thenReturn(false);

        when(notification.getReadAt())
                .thenReturn(null);

        when(notification.getCreatedAt())
                .thenReturn(createdAt);

        when(event.getType())
                .thenReturn("SURGE");

        when(event.getGameId())
                .thenReturn(2000L);

        // when
        List<NotificationResponse> result =
                notificationService.getMyNotifications(
                        " USER@EXAMPLE.COM "
                );

        // then
        assertThat(result).hasSize(1);

        NotificationResponse response = result.get(0);

        assertThat(response.notificationId()).isEqualTo(100L);
        assertThat(response.type()).isEqualTo("SURGE");
        assertThat(response.gameId()).isEqualTo(2000L);
        assertThat(response.message())
                .isEqualTo("경기 흐름이 급변하고 있어요.");
        assertThat(response.read()).isFalse();
        assertThat(response.readAt()).isNull();
        assertThat(response.createdAt()).isEqualTo(createdAt);

        /*
         * 정규화된 이메일로 회원을 조회했는지도 확인합니다.
         */
        verify(memberRepository)
                .findByEmail("user@example.com");

        /*
         * 로그인 사용자 ID와 최근 7일 cutoff가
         * Repository에 전달됐는지 확인합니다.
         */
        verify(userNotificationRepository)
                .findByMemberUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(7L),
                        any(Instant.class)
                );
    }

    /**
     * 선택 읽음 요청의 null ID와 중복 ID가 제거되는지 확인합니다.
     */
    @Test
    void markSelectedAsReadNormalizesNotificationIds() {
        // given
        Member member = mock(Member.class);

        when(memberRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(member));

        when(member.getUserId())
                .thenReturn(7L);

        /*
         * 정리된 ID [1, 2]를 읽음 처리하면
         * 두 건이 변경됐다고 가정합니다.
         */
        when(userNotificationRepository.markSelectedAsRead(
                eq(7L),
                eq(List.of(1L, 2L)),
                any(Instant.class)
        )).thenReturn(2);

        /*
         * List.of()는 null을 허용하지 않으므로
         * 테스트에서는 Arrays.asList()를 사용합니다.
         */
        List<Long> requestedIds = Arrays.asList(
                1L,
                1L,
                null,
                2L
        );

        // when
        int updatedCount =
                notificationService.markSelectedAsRead(
                        "USER@EXAMPLE.COM",
                        requestedIds
                );

        // then
        assertThat(updatedCount).isEqualTo(2);

        /*
         * 중복과 null이 제거된 [1, 2]만
         * Repository에 전달됐는지 확인합니다.
         */
        verify(userNotificationRepository).markSelectedAsRead(
                eq(7L),
                eq(List.of(1L, 2L)),
                any(Instant.class)
        );
    }

    /**
     * 읽음 처리할 ID가 비어 있으면 DB를 호출하지 않고
     * 바로 0을 반환하는지 확인합니다.
     */
    @Test
    void markSelectedAsReadReturnsZeroForEmptyIds() {
        // when
        int updatedCount =
                notificationService.markSelectedAsRead(
                        "user@example.com",
                        List.of()
                );

        // then
        assertThat(updatedCount).isZero();

        /*
         * 변경할 알림이 없으므로 회원 조회와
         * UPDATE 쿼리 모두 실행되지 않아야 합니다.
         */
        verifyNoInteractions(
                memberRepository,
                userNotificationRepository
        );
    }

    /**
     * 현재 사용자의 모든 미읽음 알림이
     * 읽음 처리되는지 확인합니다.
     */
    @Test
    void markAllAsReadUpdatesCurrentUserNotifications() {
        // given
        Member member = mock(Member.class);

        when(memberRepository.findByEmail("user@example.com"))
                .thenReturn(Optional.of(member));

        when(member.getUserId())
                .thenReturn(7L);

        when(userNotificationRepository.markAllAsRead(
                eq(7L),
                any(Instant.class)
        )).thenReturn(3);

        // when
        int updatedCount =
                notificationService.markAllAsRead(
                        "USER@EXAMPLE.COM"
                );

        // then
        assertThat(updatedCount).isEqualTo(3);

        verify(userNotificationRepository).markAllAsRead(
                eq(7L),
                any(Instant.class)
        );
    }

    /**
     * 인증 정보의 이메일에 해당하는 회원이 없으면
     * 예외가 발생하는지 확인합니다.
     */
    @Test
    void getMyNotificationsThrowsWhenMemberDoesNotExist() {
        // given
        when(memberRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                notificationService.getMyNotifications(
                        "missing@example.com"
                )
        )
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("가입되지 않은 이메일입니다.");

        /*
         * 회원을 찾지 못했으므로 알림 Repository는
         * 호출되지 않아야 합니다.
         */
        verifyNoInteractions(userNotificationRepository);
    }
}