package com.pulse.poller;

import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.message.NotificationEvent;
import com.pulse.common.message.NotificationEvent.NotificationType;
import com.pulse.common.message.NotificationEventPublisher;
import com.pulse.common.message.ScoreTaskFactory;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.domain.Game;
import com.pulse.poller.PollerGameWriter.GameUpsertResult;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameTransitionWriter {

    private final PollerGameWriter gameWriter;
    private final LiveGameCycleWriter liveGameCycleWriter;
    private final ScoreTaskFactory scoreTaskFactory;
    private final ScoreTaskPublisher scoreTaskPublisher;
    private final NotificationEventPublisher notificationEventPublisher;

    @Transactional
    public GameSyncOutcome applyTransition(BdlGame dto, TerminalDrainData drain, Instant now) {
        GameUpsertResult result = gameWriter.upsertGame(dto, now);
        boolean terminalDrainPublished = false;
        if (result.enteredTerminalState() && drain != null && !drain.plays().isEmpty()) {
            LiveGameCycleWriter.CycleWriteResult cycle =
                    liveGameCycleWriter.writeTerminalDrain(
                            result.game(), drain.plays(), drain.plateAppearances(), now);
            terminalDrainPublished = cycle.inserted() > 0;
        }
        if (result.enteredLive()) {
            notificationEventPublisher.publish(gameStartEvent(result.game(), now));
        }
        if (result.enteredTerminalState()) {
            Instant terminalObservedAt = terminalTaskObservedAt(now, terminalDrainPublished);
            scoreTaskPublisher.publish(scoreTaskFactory.terminalTask(result.game(), terminalObservedAt));
        }
        return new GameSyncOutcome(result, terminalDrainPublished);
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

    private static NotificationEvent gameStartEvent(Game game, Instant now) {
        UUID eventId = UUID.randomUUID();
        return new NotificationEvent(
                eventId,
                NotificationType.GAME_START,
                game.getId(),
                gameStartMessage(game),
                null,
                now
        );
    }

    public record GameSyncOutcome(GameUpsertResult result, boolean terminalDrainPublished) {
    }
}
