package com.pulse.replay.rescore;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("rescore")
@RequiredArgsConstructor
class HistoricalScoreReplayRunner implements ApplicationRunner {

    private final HistoricalScoreReplayService replayService;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        try {
            replayService.replayAll();
        } finally {
            applicationContext.close();
        }
    }
}
