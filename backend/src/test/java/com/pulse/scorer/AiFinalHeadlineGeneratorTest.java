package com.pulse.scorer;

import com.pulse.common.ai.AiCopyContextReader;
import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.AiCopyResult;
import com.pulse.common.ai.FinalHeadlineContext;
import com.pulse.common.ai.FinalHeadlineCopyClient;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiFinalHeadlineGeneratorTest {

    private final FinalHeadlineCopyClient finalHeadlineCopyClient = mock(FinalHeadlineCopyClient.class);
    private final AiCopyContextReader aiCopyContextReader = mock(AiCopyContextReader.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final LiveSignalPublisher liveSignalPublisher = mock(LiveSignalPublisher.class);
    private final AiFinalHeadlineGenerator generator = new AiFinalHeadlineGenerator(
            finalHeadlineCopyClient,
            aiCopyContextReader,
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
        latestContextHash(AiCopyMode.PROTECTED, "hash-final");
        latestContextHash(AiCopyMode.REVEALED, "hash-final");

        AiFinalHeadlineGenerator.GenerationStatus status = generator.generateSynchronously(100L);

        assertThat(status).isEqualTo(AiFinalHeadlineGenerator.GenerationStatus.SAVED);
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
        latestContextHash(AiCopyMode.PROTECTED, "hash-final");

        AiFinalHeadlineGenerator.GenerationStatus status = generator.generateSynchronously(100L);

        assertThat(status).isEqualTo(AiFinalHeadlineGenerator.GenerationStatus.PARTIALLY_SAVED);
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
        latestContextHash(AiCopyMode.REVEALED, "hash-final");

        AiFinalHeadlineGenerator.GenerationStatus status = generator.generateSynchronously(100L);

        assertThat(status).isEqualTo(AiFinalHeadlineGenerator.GenerationStatus.PARTIALLY_SAVED);
        assertThat(game.getFinalHeadlineProtected()).isNull();
        assertThat(game.getFinalHeadlineRevealed()).isEqualTo("홈팀이 5-3으로 승리");
        verify(gameRepository).save(game);
        verify(liveSignalPublisher).publishGameSignal(100L);
        verify(liveSignalPublisher).publishRankingSignal();
    }

    @Test
    void generate_shouldSkipWhenNoStorableHeadline() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(finalGame()));
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
    void generateSynchronously_shouldClassifyOpenAiFailureAsNotGenerated() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(finalGame()));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(openAiFailureResult()));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.empty());

        AiFinalHeadlineGenerator.GenerationStatus status = generator.generateSynchronously(100L);

        assertThat(status).isEqualTo(AiFinalHeadlineGenerator.GenerationStatus.NOT_GENERATED);
        verify(gameRepository, never()).save(any(Game.class));
    }

    @Test
    void generateSynchronously_shouldCallOnlyMissingMode() {
        Game game = finalGame();
        game.setFinalHeadlineProtected("이미 저장된 보호 헤드라인");
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.of(result("홈팀이 5-3으로 승리")));
        latestContextHash(AiCopyMode.REVEALED, "hash-final");

        AiFinalHeadlineGenerator.GenerationStatus status = generator.generateSynchronously(100L);

        assertThat(status).isEqualTo(AiFinalHeadlineGenerator.GenerationStatus.SAVED);
        assertThat(game.getFinalHeadlineProtected()).isEqualTo("이미 저장된 보호 헤드라인");
        assertThat(game.getFinalHeadlineRevealed()).isEqualTo("홈팀이 5-3으로 승리");
        verify(finalHeadlineCopyClient, never()).generateFinalHeadline(100L, AiCopyMode.PROTECTED);
        verify(gameRepository).save(game);
    }

    @Test
    void generateSynchronously_shouldSkipWhenBothModesAlreadyExist() {
        Game game = finalGame();
        game.setFinalHeadlineProtected("보호 헤드라인");
        game.setFinalHeadlineRevealed("공개 헤드라인");
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));

        AiFinalHeadlineGenerator.GenerationStatus status = generator.generateSynchronously(100L);

        assertThat(status).isEqualTo(AiFinalHeadlineGenerator.GenerationStatus.ALREADY_PRESENT);
        verify(finalHeadlineCopyClient, never()).generateFinalHeadline(
                org.mockito.ArgumentMatchers.anyLong(), any(AiCopyMode.class));
        verify(gameRepository, never()).save(any(Game.class));
    }

    @Test
    void regenerateSynchronously_shouldOverwriteBothExistingHeadlines() {
        Game game = finalGame();
        game.setFinalHeadlineProtected("기존 보호 헤드라인");
        game.setFinalHeadlineRevealed("기존 공개 헤드라인");
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(result("새 보호 헤드라인")));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.of(result("새 공개 헤드라인")));
        latestContextHash(AiCopyMode.PROTECTED, "hash-final");
        latestContextHash(AiCopyMode.REVEALED, "hash-final");

        AiFinalHeadlineGenerator.GenerationStatus status = generator.regenerateSynchronously(100L);

        assertThat(status).isEqualTo(AiFinalHeadlineGenerator.GenerationStatus.SAVED);
        assertThat(game.getFinalHeadlineProtected()).isEqualTo("새 보호 헤드라인");
        assertThat(game.getFinalHeadlineRevealed()).isEqualTo("새 공개 헤드라인");
        verify(gameRepository).save(game);
    }

    @Test
    void regenerateSynchronously_shouldPreserveExistingHeadlineWhenOneModeFails() {
        Game game = finalGame();
        game.setFinalHeadlineProtected("기존 보호 헤드라인");
        game.setFinalHeadlineRevealed("기존 공개 헤드라인");
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(result("새 보호 헤드라인")));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.of(openAiFailureResult()));
        latestContextHash(AiCopyMode.PROTECTED, "hash-final");

        AiFinalHeadlineGenerator.GenerationStatus status = generator.regenerateSynchronously(100L);

        assertThat(status).isEqualTo(AiFinalHeadlineGenerator.GenerationStatus.PARTIALLY_SAVED);
        assertThat(game.getFinalHeadlineProtected()).isEqualTo("새 보호 헤드라인");
        assertThat(game.getFinalHeadlineRevealed()).isEqualTo("기존 공개 헤드라인");
        verify(gameRepository).save(game);
    }

    @Test
    void generate_shouldSkipFallbackUsedResult() {
        Game game = finalGame();
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(fallbackUsedResult()));
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

    @Test
    void generate_shouldSkipBlankSafeTitle() {
        Game game = finalGame();
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(blankTitleResult()));
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

    @Test
    void generate_shouldSkipWhenContextHashIsNull() {
        Game game = finalGame();
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(nullContextHashResult()));
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

    @Test
    void generate_shouldSkipWhenLatestContextHashMismatch() {
        Game game = finalGame();
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(result("오래된 응답 문구", "old-hash")));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.empty());
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));
        latestContextHash(AiCopyMode.PROTECTED, "latest-hash");

        generator.generate(100L);

        assertThat(game.getFinalHeadlineProtected()).isNull();
        assertThat(game.getFinalHeadlineRevealed()).isNull();
        verify(gameRepository, never()).save(any(Game.class));
        verify(liveSignalPublisher, never()).publishGameSignal(100L);
        verify(liveSignalPublisher, never()).publishRankingSignal();
    }

    @Test
    void generate_shouldSkipWhenLatestContextIsMissing() {
        Game game = finalGame();
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.of(result("최신 컨텍스트가 없는 응답")));
        when(finalHeadlineCopyClient.generateFinalHeadline(100L, AiCopyMode.REVEALED))
                .thenReturn(Optional.empty());
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));
        when(aiCopyContextReader.finalHeadlineContext(100L, AiCopyMode.PROTECTED))
                .thenReturn(Optional.empty());

        generator.generate(100L);

        assertThat(game.getFinalHeadlineProtected()).isNull();
        assertThat(game.getFinalHeadlineRevealed()).isNull();
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

    private void latestContextHash(AiCopyMode mode, String contextHash) {
        when(aiCopyContextReader.finalHeadlineContext(100L, mode))
                .thenReturn(Optional.of(finalHeadlineContext(mode, contextHash)));
    }

    private static AiCopyResult result(String title) {
        return result(title, "hash-final");
    }

    private static AiCopyResult result(String title, String contextHash) {
        return new AiCopyResult(true, contextHash, title, List.of(), false);
    }

    private static AiCopyResult unsafeResult() {
        return new AiCopyResult(false, "hash-final", null, List.of("FORBIDDEN"), false);
    }

    private static AiCopyResult openAiFailureResult() {
        return new AiCopyResult(
                false, "hash-final", null, List.of("OPENAI_GENERATION_FAILED"), false);
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

    private static FinalHeadlineContext finalHeadlineContext(
            AiCopyMode mode,
            String contextHash
    ) {
        return new FinalHeadlineContext(
                100L,
                mode,
                Game.STATUS_FINAL,
                "경기 종료",
                List.of("후반 긴장 구간"),
                List.of("late_or_extra"),
                List.of(),
                null,
                null,
                contextHash
        );
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
