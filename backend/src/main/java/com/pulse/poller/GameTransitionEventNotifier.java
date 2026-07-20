package com.pulse.poller;

import com.pulse.common.message.NotificationEvent;
import com.pulse.common.message.NotificationEvent.NotificationType;
import com.pulse.common.message.NotificationEventPublisher;
import com.pulse.common.message.ScoreTaskFactory;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.domain.Game;
import com.pulse.poller.PollerGameWriter.GameUpsertResult;
import java.time.Instant;
import java.util.UUID;

class GameTransitionEventNotifier {

    private final ScoreTaskFactory scoreTaskFactory;
    private final ScoreTaskPublisher scoreTaskPublisher;
    private final NotificationEventPublisher notificationEventPublisher;

    GameTransitionEventNotifier(
            ScoreTaskFactory scoreTaskFactory,
            ScoreTaskPublisher scoreTaskPublisher,
            NotificationEventPublisher notificationEventPublisher
    ) {
        this.scoreTaskFactory = scoreTaskFactory;
        this.scoreTaskPublisher = scoreTaskPublisher;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    void publishTransitionEvents(GameUpsertResult result, Instant now) {
        if (result.enteredLive()) {
            UUID eventId = UUID.randomUUID();
            notificationEventPublisher.publish(new NotificationEvent(
                    eventId,
                    NotificationType.GAME_START,
                    result.game().getId(),
                    gameStartMessage(result.game()),
                    null,
                    now
            ));
        }
        if (result.enteredTerminalState()) {
            scoreTaskPublisher.publish(scoreTaskFactory.terminalTask(result.game(), now));
        }
    }

    static Instant terminalTaskObservedAt(Instant now, boolean scoreTaskPublished) {
        // 같은 tick의 live/terminal task가 outbox (game_id, observed_at) 고유키에서 충돌하지 않게 한다.
        return scoreTaskPublished ? now.plusMillis(1) : now;
    }

    private static String gameStartMessage(Game game) {
        return "관심 팀 경기가 시작됐어요 — "
                + teamLabel(game.getAwayTeamAbbr(), game.getAwayTeamName())
                + " @ "
                + teamLabel(game.getHomeTeamAbbr(), game.getHomeTeamName());
    }

    private static String teamLabel(String abbreviation, String name) {
        if (abbreviation != null && !abbreviation.isBlank()) {
            return abbreviation;
        }
        if (name != null && !name.isBlank()) {
            return name;
        }
        return "미정";
    }
}
