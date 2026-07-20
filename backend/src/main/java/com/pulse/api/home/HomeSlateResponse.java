package com.pulse.api.home;

import java.time.LocalDate;
import java.util.List;

public record HomeSlateResponse(
        LocalDate slateDate,
        List<SlateGameCard> games
) {
}
