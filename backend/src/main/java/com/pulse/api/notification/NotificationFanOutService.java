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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 하나의 알림 원본 이벤트를 사용자별 알림으로 펼쳐 저장하는 서비스입니다.
 *
 * Fan-out 의미:
 *
 * 하나의 GAME_START 또는 SURGE 이벤트가 들어오면
 * 해당 알림을 받을 사용자들을 조회한 뒤,
 * 각 사용자마다 user_notifications 행을 생성합니다.
 *
 * 예:
 *
 * notification_events
 * - GAME_START 이벤트 1행
 *
 * 대상 사용자
 * - 사용자 A
 * - 사용자 B
 * - 사용자 C
 *
 * user_notifications
 * - 이벤트 + 사용자 A
 * - 이벤트 + 사용자 B
 * - 이벤트 + 사용자 C
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationFanOutService {

    /**
     * RabbitMQ로 전달된 eventId에 대응하는
     * 원본 notification_events 행이 실제로 존재하는지 확인합니다.
     */
    private final NotificationEventLogRepository notificationEventLogRepository;

    /**
     * GAME_START 이벤트의 gameId로 경기 정보를 조회합니다.
     *
     * 조회한 Game에서 홈팀 ID와 원정팀 ID를 가져옵니다.
     */
    private final GameRepository gameRepository;

    /**
     * GAME_START 대상 사용자를 조회합니다.
     *
     * 홈팀 또는 원정팀을 관심팀으로 등록했고,
     * 경기 시작 알림이 켜진 ACTIVE 사용자를 찾습니다.
     */
    private final UserFavoriteTeamRepository userFavoriteTeamRepository;

    /**
     * SURGE 대상 사용자를 조회합니다.
     *
     * 중요한 순간 알림이 켜진 ACTIVE 사용자를 찾습니다.
     */
    private final UserSettingRepository userSettingRepository;

    /**
     * 대상 사용자별 user_notifications 데이터를 저장합니다.
     *
     * insertIfAbsent()가 PostgreSQL의
     * ON CONFLICT DO NOTHING을 사용하므로
     * 같은 이벤트가 재전달돼도 중복 저장되지 않습니다.
     */
    private final UserNotificationRepository userNotificationRepository;

    /**
     * RabbitMQ에서 받은 알림 이벤트를 사용자별 알림으로 저장합니다.
     *
     * 처리 순서:
     *
     * 1. 메시지 null 여부 검사
     * 2. 원본 notification_events 행 확인
     * 3. 알림 타입별 대상 사용자 조회
     * 4. 사용자별 user_notifications 저장
     *
     * @Transactional을 사용하는 이유:
     *
     * 여러 사용자의 알림을 저장하는 중간에 오류가 발생하면
     * 일부 사용자만 저장되고 나머지는 저장되지 않는 상태를 막습니다.
     *
     * 오류 발생 시 전체 트랜잭션을 롤백하고,
     * 예외가 Listener까지 전달되도록 예외를 잡아서 숨기지 않습니다.
     *
     * @param event RabbitMQ notify.events에서 받은 알림 이벤트
     */
    @Transactional
    public void fanOut(NotificationEvent event) {
        Objects.requireNonNull(
                event,
                "알림 이벤트는 필수입니다."
        );

        /*
         * user_notifications.event_id는
         * notification_events.event_id를 FK로 참조합니다.
         *
         * 따라서 사용자별 알림을 저장하기 전에
         * 원본 이벤트가 실제 DB에 존재하는지 확인합니다.
         */
        NotificationEventLog eventLog =
                notificationEventLogRepository.findById(event.eventId())
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "원본 알림 이벤트를 찾을 수 없습니다. eventId="
                                                + event.eventId()
                                )
                        );

        /*
         * RabbitMQ 메시지와 DB 원본 이벤트가 같은 유형인지 확인합니다.
         *
         * 데이터가 불일치하면 잘못된 사용자에게 알림이 생성될 수 있으므로
         * 조용히 무시하지 않고 예외를 발생시킵니다.
         */
        validateEventConsistency(event, eventLog);

        List<Member> targets = findTargets(event);

        /*
         * 한 이벤트의 사용자별 알림들은 같은 Consumer 실행에서 생성되므로,
         * 한 번 구한 시각을 모든 대상 사용자에게 공통으로 사용합니다.
         */
        Instant createdAt = Instant.now();

        int insertedCount = 0;

        for (Member target : targets) {
            /*
             * 반환값:
             * 1 → 새 알림 저장
             * 0 → 이미 같은 eventId + userId 알림이 존재함
             */
            int inserted = userNotificationRepository.insertIfAbsent(
                    event.eventId(),
                    target.getUserId(),
                    event.message(),
                    createdAt
            );

            insertedCount += inserted;
        }

        log.info(
                "알림 fan-out 완료: eventId={}, type={}, targetCount={}, insertedCount={}",
                event.eventId(),
                event.type(),
                targets.size(),
                insertedCount
        );
    }

    /**
     * 알림 타입에 따라 대상 사용자 조회 방법을 선택합니다.
     *
     * GAME_START:
     * - 경기 홈팀/원정팀을 관심팀으로 선택한 사용자
     * - 경기 시작 알림 ON
     * - ACTIVE 사용자
     *
     * SURGE:
     * - 중요한 순간 알림 ON
     * - 관심팀과 무관
     * - ACTIVE 사용자
     */
    private List<Member> findTargets(NotificationEvent event) {
        return switch (event.type()) {
            case GAME_START -> findGameStartTargets(event.gameId());
            case SURGE -> findSurgeTargets();
        };
    }

    /**
     * GAME_START 알림 대상 사용자를 조회합니다.
     *
     * @param gameId 경기 ID
     * @return 관심팀 경기 시작 알림 대상 사용자
     */
    private List<Member> findGameStartTargets(long gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "경기 시작 알림의 경기 정보를 찾을 수 없습니다. gameId="
                                        + gameId
                        )
                );

        /*
         * Game에는 Team 연관 객체가 아니라
         * homeTeamId와 awayTeamId가 직접 저장되어 있습니다.
         *
         * null 값을 제거하고 중복 팀 ID도 제거합니다.
         *
         * 홈팀과 원정팀 ID가 같은 정상 경기는 없지만,
         * 데이터 오류가 있더라도 같은 값을 두 번 조회하지 않도록 distinct 처리합니다.
         */
        List<Long> teamIds = java.util.stream.Stream.of(
                        game.getHomeTeamId(),
                        game.getAwayTeamId()
                )
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        /*
         * 팀 ID가 하나도 없다면 정상적으로 대상 사용자를 고를 수 없습니다.
         *
         * 빈 대상이라고 조용히 끝내면 데이터 문제를 발견하기 어려우므로
         * 예외를 발생시켜 RabbitMQ 재전달 및 DLQ 흐름으로 넘깁니다.
         */
        if (teamIds.isEmpty()) {
            throw new IllegalStateException(
                    "경기의 홈팀/원정팀 정보가 없습니다. gameId="
                            + gameId
            );
        }

        return userFavoriteTeamRepository
                .findGameStartNotificationTargets(
                        teamIds,
                        MemberStatus.ACTIVE
                );
    }

    /**
     * SURGE 알림 대상 사용자를 조회합니다.
     *
     * SURGE는 관심팀과 관계없이
     * 중요한 순간 알림을 켠 ACTIVE 사용자 전체가 대상입니다.
     */
    private List<Member> findSurgeTargets() {
        return userSettingRepository
                .findImportantMomentNotificationTargets(
                        MemberStatus.ACTIVE
                );
    }

    /**
     * RabbitMQ 메시지와 DB 원본 이벤트가 같은 사건인지 검증합니다.
     *
     * eventId는 이미 Repository 조회에 사용했으므로 같고,
     * 여기서는 알림 타입과 경기 ID를 추가로 확인합니다.
     */
    private void validateEventConsistency(
            NotificationEvent event,
            NotificationEventLog eventLog
    ) {
        String expectedType = event.type().name();

        if (!expectedType.equals(eventLog.getType())) {
            throw new IllegalStateException(
                    "알림 타입이 원본 이벤트와 일치하지 않습니다. eventId="
                            + event.eventId()
                            + ", messageType="
                            + expectedType
                            + ", storedType="
                            + eventLog.getType()
            );
        }

        if (!Objects.equals(event.gameId(), eventLog.getGameId())) {
            throw new IllegalStateException(
                    "경기 ID가 원본 이벤트와 일치하지 않습니다. eventId="
                            + event.eventId()
                            + ", messageGameId="
                            + event.gameId()
                            + ", storedGameId="
                            + eventLog.getGameId()
            );
        }
    }
}