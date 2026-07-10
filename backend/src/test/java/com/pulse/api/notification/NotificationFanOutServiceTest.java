package com.pulse.api.notification;

import com.pulse.api.notification.domain.UserNotificationRepository;
import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberStatus;
import com.pulse.api.user.domain.UserFavoriteTeamRepository;
import com.pulse.api.user.domain.UserSettingRepository;
import com.pulse.common.message.NotificationEvent;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.NotificationEventLog;
import com.pulse.domain.NotificationEventLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationFanOutService 단위 테스트입니다.
 *
 * RabbitMQ와 실제 DB를 실행하지 않고 Repository를 Mock으로 대체해
 * 알림 유형별 대상 사용자 조회와 사용자별 알림 저장 흐름을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class NotificationFanOutServiceTest {

    @Mock
    private NotificationEventLogRepository notificationEventLogRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private UserFavoriteTeamRepository userFavoriteTeamRepository;

    @Mock
    private UserSettingRepository userSettingRepository;

    @Mock
    private UserNotificationRepository userNotificationRepository;

    @InjectMocks
    private NotificationFanOutService notificationFanOutService;

    /**
     * GAME_START 이벤트가 들어오면:
     *
     * 1. 원본 알림 이벤트 조회
     * 2. 경기 조회
     * 3. 홈팀·원정팀 관심 사용자 조회
     * 4. 각 사용자에게 알림 저장
     *
     * 순서로 처리되는지 검증합니다.
     */
    @Test
    void fanOut_gameStart_shouldSaveNotificationsForFavoriteTeamUsers() {
        // given
        UUID eventId = UUID.randomUUID();
        long gameId = 100L;

        NotificationEvent event = new NotificationEvent(
                eventId,
                NotificationEvent.NotificationType.GAME_START,
                gameId,
                "관심팀 경기가 시작되었습니다.",
                "GAME_START",
                Instant.parse("2026-07-10T08:00:00Z")
        );

        NotificationEventLog eventLog =
                org.mockito.Mockito.mock(NotificationEventLog.class);

        Game game = org.mockito.Mockito.mock(Game.class);

        Member firstMember = org.mockito.Mockito.mock(Member.class);
        Member secondMember = org.mockito.Mockito.mock(Member.class);

        /*
         * RabbitMQ 메시지와 notification_events 원본 데이터가
         * 동일한 사건이라고 가정합니다.
         */
        when(notificationEventLogRepository.findById(eventId))
                .thenReturn(Optional.of(eventLog));

        when(eventLog.getType()).thenReturn("GAME_START");
        when(eventLog.getGameId()).thenReturn(gameId);

        /*
         * 경기의 홈팀과 원정팀 ID입니다.
         */
        when(gameRepository.findById(gameId))
                .thenReturn(Optional.of(game));

        when(game.getHomeTeamId()).thenReturn(14L);
        when(game.getAwayTeamId()).thenReturn(19L);

        /*
         * 홈팀 또는 원정팀을 관심팀으로 등록한 ACTIVE 사용자입니다.
         */
        when(
                userFavoriteTeamRepository
                        .findGameStartNotificationTargets(
                                any(),
                                eq(MemberStatus.ACTIVE)
                        )
        ).thenReturn(List.of(firstMember, secondMember));

        when(firstMember.getUserId()).thenReturn(1L);
        when(secondMember.getUserId()).thenReturn(2L);

        when(
                userNotificationRepository.insertIfAbsent(
                        eq(eventId),
                        any(Long.class),
                        eq(event.message()),
                        any(Instant.class)
                )
        ).thenReturn(1);

        // when
        notificationFanOutService.fanOut(event);

        // then
        verify(notificationEventLogRepository)
                .findById(eventId);

        verify(gameRepository)
                .findById(gameId);

        /*
         * 홈팀 14와 원정팀 19가 대상 팀 목록에 포함됐는지 확인합니다.
         */
        verify(userFavoriteTeamRepository)
                .findGameStartNotificationTargets(
                        argThat(teamIds ->
                                teamIds.size() == 2
                                        && teamIds.contains(14L)
                                        && teamIds.contains(19L)
                        ),
                        eq(MemberStatus.ACTIVE)
                );

        /*
         * GAME_START에서는 SURGE 설정 조회가 실행되면 안 됩니다.
         */
        verify(userSettingRepository, never())
                .findImportantMomentNotificationTargets(
                        any(MemberStatus.class)
                );

        verify(userNotificationRepository)
                .insertIfAbsent(
                        eq(eventId),
                        eq(1L),
                        eq(event.message()),
                        any(Instant.class)
                );

        verify(userNotificationRepository)
                .insertIfAbsent(
                        eq(eventId),
                        eq(2L),
                        eq(event.message()),
                        any(Instant.class)
                );
    }

    /**
     * SURGE 이벤트는 관심팀과 관계없이
     * 중요한 순간 알림을 켠 ACTIVE 사용자를 대상으로 저장하는지 검증합니다.
     */
    @Test
    void fanOut_surge_shouldSaveNotificationsForImportantMomentUsers() {
        // given
        UUID eventId = UUID.randomUUID();
        long gameId = 200L;

        NotificationEvent event = new NotificationEvent(
                eventId,
                NotificationEvent.NotificationType.SURGE,
                gameId,
                "경기 흐름이 급격하게 바뀌고 있습니다.",
                "SURGE",
                Instant.parse("2026-07-10T08:10:00Z")
        );

        NotificationEventLog eventLog =
                org.mockito.Mockito.mock(NotificationEventLog.class);

        Member targetMember =
                org.mockito.Mockito.mock(Member.class);

        when(notificationEventLogRepository.findById(eventId))
                .thenReturn(Optional.of(eventLog));

        when(eventLog.getType()).thenReturn("SURGE");
        when(eventLog.getGameId()).thenReturn(gameId);

        when(
                userSettingRepository
                        .findImportantMomentNotificationTargets(
                                MemberStatus.ACTIVE
                        )
        ).thenReturn(List.of(targetMember));

        when(targetMember.getUserId()).thenReturn(3L);

        when(
                userNotificationRepository.insertIfAbsent(
                        eq(eventId),
                        eq(3L),
                        eq(event.message()),
                        any(Instant.class)
                )
        ).thenReturn(1);

        // when
        notificationFanOutService.fanOut(event);

        // then
        verify(userSettingRepository)
                .findImportantMomentNotificationTargets(
                        MemberStatus.ACTIVE
                );

        /*
         * SURGE는 경기의 홈팀·원정팀이나 관심팀을 조회하지 않습니다.
         */
        verify(gameRepository, never())
                .findById(any(Long.class));

        verify(userFavoriteTeamRepository, never())
                .findGameStartNotificationTargets(
                        any(),
                        any(MemberStatus.class)
                );

        verify(userNotificationRepository)
                .insertIfAbsent(
                        eq(eventId),
                        eq(3L),
                        eq(event.message()),
                        any(Instant.class)
                );
    }

    /**
     * 같은 eventId + userId 알림이 이미 존재해
     * insertIfAbsent()가 0을 반환하더라도,
     * 서비스가 오류 없이 처리를 마치는지 검증합니다.
     *
     * 실제 중복 차단은 PostgreSQL의
     * ON CONFLICT (event_id, user_id) DO NOTHING이 담당합니다.
     */
    @Test
    void fanOut_shouldCompleteWhenNotificationAlreadyExists() {
        // given
        UUID eventId = UUID.randomUUID();
        long gameId = 300L;

        NotificationEvent event = new NotificationEvent(
                eventId,
                NotificationEvent.NotificationType.SURGE,
                gameId,
                "이미 저장된 이벤트입니다.",
                "SURGE",
                Instant.parse("2026-07-10T08:20:00Z")
        );

        NotificationEventLog eventLog =
                org.mockito.Mockito.mock(NotificationEventLog.class);

        Member targetMember =
                org.mockito.Mockito.mock(Member.class);

        when(notificationEventLogRepository.findById(eventId))
                .thenReturn(Optional.of(eventLog));

        when(eventLog.getType()).thenReturn("SURGE");
        when(eventLog.getGameId()).thenReturn(gameId);

        when(
                userSettingRepository
                        .findImportantMomentNotificationTargets(
                                MemberStatus.ACTIVE
                        )
        ).thenReturn(List.of(targetMember));

        when(targetMember.getUserId()).thenReturn(4L);

        /*
         * 0은 같은 event_id + user_id가 이미 존재해서
         * INSERT가 실행되지 않았다는 뜻입니다.
         */
        when(
                userNotificationRepository.insertIfAbsent(
                        eq(eventId),
                        eq(4L),
                        eq(event.message()),
                        any(Instant.class)
                )
        ).thenReturn(0);

        // when & then
        assertDoesNotThrow(
                () -> notificationFanOutService.fanOut(event)
        );

        verify(userNotificationRepository)
                .insertIfAbsent(
                        eq(eventId),
                        eq(4L),
                        eq(event.message()),
                        any(Instant.class)
                );
    }
}