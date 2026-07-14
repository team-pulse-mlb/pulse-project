package com.pulse.scorer;

import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.AiCopyResult;
import com.pulse.common.ai.FinalHeadlineCopyClient;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        verify(liveSignalPublisher).publishRankingSignal();
    }

    @Test
    void generate_shouldSaveOnlyRevealedWhenProtectedMissing() {
        Game game = finalGame();
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.empty());
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.of(result("홈팀이 5-3으로 승리")));
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));

        generator.generate(100L);

        assertThat(game.getFinalHeadlineProtected()).isNull();
        assertThat(game.getFinalHeadlineRevealed()).isEqualTo("홈팀이 5-3으로 승리");
        verify(gameRepository).save(game);
        verify(liveSignalPublisher).publishGameSignal(100L);
        verify(liveSignalPublisher).publishRankingSignal();
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

    @Test
    void generate_shouldSkipFallbackUsedResult() {
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(fallbackUsedResult()));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.empty());

        generator.generate(100L);

        verify(gameRepository, never()).save(any(Game.class));
        verify(liveSignalPublisher, never()).publishGameSignal(100L);
        verify(liveSignalPublisher, never()).publishRankingSignal();
    }

    @Test
    void generate_shouldSkipBlankSafeTitle() {
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(blankTitleResult()));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.empty());

        generator.generate(100L);

        verify(gameRepository, never()).save(any(Game.class));
        verify(liveSignalPublisher, never()).publishGameSignal(100L);
        verify(liveSignalPublisher, never()).publishRankingSignal();
    }

    @Test
    void generate_shouldSkipWhenContextHashIsNull() {
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(nullContextHashResult()));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.empty());

        generator.generate(100L);

        verify(gameRepository, never()).save(any(Game.class));
        verify(liveSignalPublisher, never()).publishGameSignal(100L);
        verify(liveSignalPublisher, never()).publishRankingSignal();
    }

    @Test
    void generate_shouldSkipWhenGameDoesNotExist() {
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(result("접전 끝에 마무리된 경기")));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.empty());
        when(gameRepository.findById(100L)).thenReturn(Optional.empty());

        generator.generate(100L);

        verify(gameRepository, never()).save(any(Game.class));
        verify(liveSignalPublisher, never()).publishGameSignal(100L);
        verify(liveSignalPublisher, never()).publishRankingSignal();
    }

    @Test
    void generate_shouldSkipWhenGameIsNotFinal() {
        Game game = nonFinalGame();
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(result("접전 끝에 마무리된 경기")));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.empty());
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));

        generator.generate(100L);

        assertThat(game.getFinalHeadlineProtected()).isNull();
        assertThat(game.getFinalHeadlineRevealed()).isNull();
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

    private static AiCopyResult fallbackUsedResult() {
        return new AiCopyResult(true, "hash-final", "fallback 문구", List.of(), true);
    }

    private static AiCopyResult blankTitleResult() {
        return new AiCopyResult(true, "hash-final", "   ", List.of(), false);
    }

    private static AiCopyResult nullContextHashResult() {
        return new AiCopyResult(true, null, "접전 끝에 마무리된 경기", List.of(), false);
    }

    private static Game finalGame() {
        Game game = new Game();
        game.setId(100L);
        game.setStatus(Game.STATUS_FINAL);
        return game;
    }

    private static Game nonFinalGame() {
        Game game = new Game();
        game.setId(100L);
        game.setStatus("STATUS_IN_PROGRESS");
        return game;
    }
}
