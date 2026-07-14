package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.AiCopyResult;
import com.pulse.common.ai.FinalHeadlineCopyClient;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiFinalHeadlineGeneratorTest {

    private final FinalHeadlineCopyClient finalHeadlineCopyClient = mock(FinalHeadlineCopyClient.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final LiveSignalPublisher liveSignalPublisher = mock(LiveSignalPublisher.class);
    private final AiFinalHeadlineGenerator generator = new AiFinalHeadlineGenerator(
            finalHeadlineCopyClient,
            gameRepository,
            liveSignalPublisher
    );

    @Test
    void generate_shouldSaveBothHeadlinesAndPublishSignals() {
        Game game = finalGame();
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(result("접전 끝에 마무리된 경기")));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.of(result("홈팀이 5-3으로 승리")));
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));

        generator.generate(100L);

        assertThat(game.getFinalHeadlineProtected()).isEqualTo("접전 끝에 마무리된 경기");
        assertThat(game.getFinalHeadlineRevealed()).isEqualTo("홈팀이 5-3으로 승리");
        verify(gameRepository).save(game);
        verify(liveSignalPublisher).publishGameSignal(100L);
        verify(liveSignalPublisher).publishRankingSignal();
    }

    @Test
    void generate_shouldSaveOnlyProtectedWhenRevealedMissing() {
        Game game = finalGame();
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(result("접전 끝에 마무리된 경기")));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.empty());
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));

        generator.generate(100L);

        assertThat(game.getFinalHeadlineProtected()).isEqualTo("접전 끝에 마무리된 경기");
        assertThat(game.getFinalHeadlineRevealed()).isNull();
        verify(gameRepository).save(game);
        verify(liveSignalPublisher).publishGameSignal(100L);
    }

    @Test
    void generate_shouldSkipWhenNoStorableHeadline() {
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(unsafeResult()));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.empty());

        generator.generate(100L);

        verify(gameRepository, never()).save(any(Game.class));
        verify(liveSignalPublisher, never()).publishGameSignal(100L);
        verify(liveSignalPublisher, never()).publishRankingSignal();
    }

    private static AiCopyResult result(String title) {
        return new AiCopyResult(true, "hash-final", title, List.of(), false);
    }

    private static AiCopyResult unsafeResult() {
        return new AiCopyResult(false, "hash-final", null, List.of("FORBIDDEN"), false);
    }

    private static Game finalGame() {
        Game game = new Game();
        game.setId(100L);
        game.setStatus(Game.STATUS_FINAL);
        return game;
    }
}
