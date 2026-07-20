package com.pulse.api.home;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
        description = "경기 상태에 따라 필드가 달라지는 홈 슬레이트 카드",
        oneOf = {
                SlateLiveGameCard.class,
                SlateScheduledGameCard.class,
                SlateFinishedGameCard.class
        }
)
public interface SlateGameCard {
    long gameId();

    String gameState();

    Instant startTime();
}
