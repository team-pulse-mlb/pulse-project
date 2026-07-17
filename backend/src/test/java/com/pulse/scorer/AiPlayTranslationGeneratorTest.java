package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.ai.AiPlayTranslationRequest;
import com.pulse.ai.AiPlayTranslationResponse;
import com.pulse.ai.AiServiceClient;
import com.pulse.common.ai.AiContextHashCalculator;
import com.pulse.common.ai.AiCopyContextReader;
import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.FinalHeadlineContext;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiPlayTranslationGeneratorTest {

    private static final long GAME_ID = 10L;
    private static final long PLAY_ID = 20L;

    private final AiServiceClient aiServiceClient =
            mock(AiServiceClient.class);

    private final PlayRepository playRepository =
            mock(PlayRepository.class);

    private final LiveSignalPublisher liveSignalPublisher =
            mock(LiveSignalPublisher.class);

    private final AiCopyContextReader aiCopyContextReader =
            mock(AiCopyContextReader.class);

    private final GameRepository gameRepository =
            mock(GameRepository.class);

    private final AiFinalHeadlineGenerator aiFinalHeadlineGenerator =
            mock(AiFinalHeadlineGenerator.class);

    private final AiPlayTranslationGenerator generator =
            new AiPlayTranslationGenerator(
                    aiServiceClient,
                    playRepository,
                    liveSignalPublisher,
                    new PlayTranslationProperties(
                            3,
                            10),
                    aiCopyContextReader,
                    gameRepository,
                    aiFinalHeadlineGenerator);

    @Test
    void generatePending_shouldRegenerateRevealedAfterImportantTranslationSaved() {
        Play play =
                play("Soto singled to center.");

        stubSuccessfulTranslation(play);

        when(
                playRepository.findPendingPlayTranslations(
                        eq(GAME_ID),
                        eq(100L),
                        eq(3),
                        any()
                )
        ).thenReturn(List.of(play));

        when(
                aiCopyContextReader.finalHeadlineContext(
                        GAME_ID,
                        AiCopyMode.REVEALED
                )
        ).thenReturn(
                Optional.of(
                        revealedContext(
                                List.of(
                                        "DECISIVE_SCORE",
                                        "HIT"
                                )
                        )
                )
        );

        when(
                gameRepository
                        .markFinalHeadlineRevealedRegenerationAttempted(
                                eq(GAME_ID),
                                any(Instant.class)
                        )
        ).thenReturn(1);

        when(
                aiFinalHeadlineGenerator
                        .regenerateRevealedSynchronously(
                                GAME_ID
                        )
        ).thenReturn(
                AiFinalHeadlineGenerator
                        .GenerationStatus
                        .SAVED
        );

        generator.generatePending(
                GAME_ID,
                100L
        );

        assertThat(play.getTextKo())
                .isEqualTo(
                        "Soto, 중견수 방면 안타"
                );

        verify(
                gameRepository
        ).markFinalHeadlineRevealedRegenerationAttempted(
                eq(GAME_ID),
                any(Instant.class)
        );

        verify(
                aiFinalHeadlineGenerator
        ).regenerateRevealedSynchronously(
                GAME_ID
        );
    }

    @Test
    void generatePending_shouldNotRegenerateWhenDatabaseClaimAlreadyUsed() {
        Play play =
                play("Soto singled to center.");

        stubSuccessfulTranslation(play);

        when(
                playRepository.findPendingPlayTranslations(
                        eq(GAME_ID),
                        eq(100L),
                        eq(3),
                        any()
                )
        ).thenReturn(List.of(play));

        when(
                aiCopyContextReader.finalHeadlineContext(
                        GAME_ID,
                        AiCopyMode.REVEALED
                )
        ).thenReturn(
                Optional.of(
                        revealedContext(
                                List.of(
                                        "DECISIVE_SCORE",
                                        "HIT"
                                )
                        )
                )
        );

        when(
                gameRepository
                        .markFinalHeadlineRevealedRegenerationAttempted(
                                eq(GAME_ID),
                                any(Instant.class)
                        )
        ).thenReturn(0);

        generator.generatePending(
                GAME_ID,
                100L
        );

        verify(
                aiFinalHeadlineGenerator,
                never()
        ).regenerateRevealedSynchronously(
                GAME_ID
        );
    }

    @Test
    void generatePending_shouldNotRegenerateForNonImportantTranslation() {
        Play play =
                play("Soto singled to center.");

        stubSuccessfulTranslation(play);

        when(
                playRepository.findPendingPlayTranslations(
                        eq(GAME_ID),
                        eq(100L),
                        eq(3),
                        any()
                )
        ).thenReturn(List.of(play));

        when(
                aiCopyContextReader.finalHeadlineContext(
                        GAME_ID,
                        AiCopyMode.REVEALED
                )
        ).thenReturn(
                Optional.of(
                        revealedContext(
                                List.of(
                                        "SCORING_PLAY",
                                        "HIT"
                                )
                        )
                )
        );

        generator.generatePending(
                GAME_ID,
                100L
        );

        verify(
                gameRepository,
                never()
        ).markFinalHeadlineRevealedRegenerationAttempted(
                eq(GAME_ID),
                any(Instant.class)
        );

        verify(
                aiFinalHeadlineGenerator,
                never()
        ).regenerateRevealedSynchronously(
                GAME_ID
        );
    }

    @Test
    void generateSynchronously_shouldSaveTranslationAndPublishGameSignal() {
        Play play =
                play(
                        "Soto singled to center.");

        String contextHash =
                AiContextHashCalculator
                        .calculatePlayTranslation(
                                GAME_ID,
                                PLAY_ID,
                                "Soto singled to center.");

        AiPlayTranslationResponse response =
                new AiPlayTranslationResponse(
                        "  Soto, 중견수 방면 안타  ",
                        List.of(),
                        false,
                        contextHash);

        /*
         * 첫 조회는 AI 요청 생성용이고,
         * 두 번째 조회는 응답 이후 stale 여부 확인용이다.
         */
        when(playRepository.findById(PLAY_ID))
                .thenReturn(
                        Optional.of(play),
                        Optional.of(play));

        when(aiServiceClient.generatePlayTranslation(any()))
                .thenReturn(
                        Optional.of(response));

        AiPlayTranslationGenerator.GenerationStatus status =
                generator.generateSynchronously(
                        GAME_ID,
                        PLAY_ID);

        assertThat(status)
                .isEqualTo(
                        AiPlayTranslationGenerator
                                .GenerationStatus
                                .SAVED);

        assertThat(play.getTextKo())
                .isEqualTo(
                        "Soto, 중견수 방면 안타");

        assertThat(play.getTextKoContextHash())
                .isEqualTo(
                        contextHash);

        ArgumentCaptor<AiPlayTranslationRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        AiPlayTranslationRequest.class);

        verify(aiServiceClient)
                .generatePlayTranslation(
                        requestCaptor.capture());

        AiPlayTranslationRequest request =
                requestCaptor.getValue();

        assertThat(request.gameId())
                .isEqualTo(GAME_ID);
        assertThat(request.playId())
                .isEqualTo(PLAY_ID);
        assertThat(request.mode())
                .isEqualTo("REVEALED");
        assertThat(request.sourceText())
                .isEqualTo("Soto singled to center.");
        assertThat(request.targetLanguage())
                .isEqualTo("ko");
        assertThat(request.contextHash())
                .isEqualTo(contextHash);

        verify(playRepository, times(2))
                .save(play);

        verify(liveSignalPublisher)
                .publishGameSignal(GAME_ID);
    }

    @Test
    void generateSynchronously_shouldDiscardResponseWhenSourceTextChanged() {
        Play requestedPlay =
                play(
                        "Soto singled to center.");

        Play latestPlay =
                play(
                        "Soto doubled to right field.");

        String requestedContextHash =
                AiContextHashCalculator
                        .calculatePlayTranslation(
                                GAME_ID,
                                PLAY_ID,
                                requestedPlay.getText());

        AiPlayTranslationResponse response =
                new AiPlayTranslationResponse(
                        "Soto, 중견수 방면 안타",
                        List.of(),
                        false,
                        requestedContextHash);

        when(playRepository.findById(PLAY_ID))
                .thenReturn(
                        Optional.of(requestedPlay),
                        Optional.of(latestPlay));

        when(aiServiceClient.generatePlayTranslation(any()))
                .thenReturn(
                        Optional.of(response));

        AiPlayTranslationGenerator.GenerationStatus status =
                generator.generateSynchronously(
                        GAME_ID,
                        PLAY_ID);

        assertThat(status)
                .isEqualTo(
                        AiPlayTranslationGenerator
                                .GenerationStatus
                                .STALE);

        assertThat(latestPlay.getTextKo())
                .isNull();

        assertThat(requestedPlay.getTextKoAttempts())
                .isEqualTo(1);

        verify(playRepository)
                .save(requestedPlay);

        verify(liveSignalPublisher, never())
                .publishGameSignal(GAME_ID);
    }

    @Test
    void generateSynchronously_shouldReturnCallFailedForOpenAiFailure() {
        Play play =
                play(
                        "Soto singled to center.");

        String contextHash =
                AiContextHashCalculator
                        .calculatePlayTranslation(
                                GAME_ID,
                                PLAY_ID,
                                play.getText());

        AiPlayTranslationResponse response =
                new AiPlayTranslationResponse(
                        null,
                        List.of(
                                "OPENAI_TIMEOUT"),
                        false,
                        contextHash);

        when(playRepository.findById(PLAY_ID))
                .thenReturn(
                        Optional.of(play));

        when(aiServiceClient.generatePlayTranslation(any()))
                .thenReturn(
                        Optional.of(response));

        AiPlayTranslationGenerator.GenerationStatus status =
                generator.generateSynchronously(
                        GAME_ID,
                        PLAY_ID);

        assertThat(status)
                .isEqualTo(
                        AiPlayTranslationGenerator
                                .GenerationStatus
                                .CALL_FAILED);

        assertThat(play.getTextKoAttempts())
                .isEqualTo(1);

        verify(playRepository)
                .save(play);

        verify(liveSignalPublisher, never())
                .publishGameSignal(GAME_ID);
    }

    @Test
    void generateSynchronously_shouldSaveAttemptWhenReviewRejected() {
        Play play =
                play(
                        "Pages walked.");

        String contextHash =
                AiContextHashCalculator
                        .calculatePlayTranslation(
                                GAME_ID,
                                PLAY_ID,
                                play.getText());

        AiPlayTranslationResponse response =
                new AiPlayTranslationResponse(
                        null,
                        List.of(
                                "PLAYER_NAME_NOT_PRESERVED"),
                        false,
                        contextHash);

        when(playRepository.findById(PLAY_ID))
                .thenReturn(
                        Optional.of(play));

        when(aiServiceClient.generatePlayTranslation(any()))
                .thenReturn(
                        Optional.of(response));

        AiPlayTranslationGenerator.GenerationStatus status =
                generator.generateSynchronously(
                        GAME_ID,
                        PLAY_ID);

        assertThat(status)
                .isEqualTo(
                        AiPlayTranslationGenerator
                                .GenerationStatus
                                .REVIEW_REJECTED);

        assertThat(play.getTextKoAttempts())
                .isEqualTo(1);

        verify(playRepository)
                .save(play);

        verify(liveSignalPublisher, never())
                .publishGameSignal(GAME_ID);
    }

    @Test
    void generateSynchronously_shouldSkipWhenAttemptsAreExhausted() {
        Play play =
                play(
                        "Pages walked.");

        play.setTextKoAttempts(3);

        when(playRepository.findById(PLAY_ID))
                .thenReturn(
                        Optional.of(play));

        AiPlayTranslationGenerator.GenerationStatus status =
                generator.generateSynchronously(
                        GAME_ID,
                        PLAY_ID);

        assertThat(status)
                .isEqualTo(
                        AiPlayTranslationGenerator
                                .GenerationStatus
                                .ATTEMPTS_EXHAUSTED);

        verify(aiServiceClient, never())
                .generatePlayTranslation(any());

        verify(playRepository, never())
                .save(any(Play.class));
    }

    @Test
    void generateSynchronously_shouldSkipPlayWithExistingTranslation() {
        Play play =
                play(
                        "Soto singled to center.");

        play.setTextKo(
                "Soto, 중견수 방면 안타");

        when(playRepository.findById(PLAY_ID))
                .thenReturn(
                        Optional.of(play));

        AiPlayTranslationGenerator.GenerationStatus status =
                generator.generateSynchronously(
                        GAME_ID,
                        PLAY_ID);

        assertThat(status)
                .isEqualTo(
                        AiPlayTranslationGenerator
                                .GenerationStatus
                                .ALREADY_PRESENT);

        verify(aiServiceClient, never())
                .generatePlayTranslation(
                        any());

        verify(playRepository, never())
                .save(any(Play.class));
    }

    @Test
    void regenerateSynchronously_shouldOverwriteExistingTranslation() {
        Play play =
                play(
                        "Soto singled to center.");

        play.setTextKo("기존 번역");
        play.setTextKoAttempts(3);

        String contextHash =
                AiContextHashCalculator
                        .calculatePlayTranslation(
                                GAME_ID,
                                PLAY_ID,
                                play.getText());

        AiPlayTranslationResponse response =
                new AiPlayTranslationResponse(
                        "Soto, 중견수 방면 안타",
                        List.of(),
                        false,
                        contextHash);

        when(playRepository.findById(PLAY_ID))
                .thenReturn(
                        Optional.of(play),
                        Optional.of(play));

        when(aiServiceClient.generatePlayTranslation(any()))
                .thenReturn(
                        Optional.of(response));

        AiPlayTranslationGenerator.GenerationStatus status =
                generator.regenerateSynchronously(
                        GAME_ID,
                        PLAY_ID);

        assertThat(status)
                .isEqualTo(
                        AiPlayTranslationGenerator
                                .GenerationStatus
                                .SAVED);

        assertThat(play.getTextKo())
                .isEqualTo(
                        "Soto, 중견수 방면 안타");

        verify(liveSignalPublisher)
                .publishGameSignal(GAME_ID);
    }

    @Test
    void regenerateSynchronously_shouldPreserveExistingTranslationWhenRejected() {
        Play play =
                play(
                        "Soto singled to center.");

        play.setTextKo("기존 번역");

        String contextHash =
                AiContextHashCalculator
                        .calculatePlayTranslation(
                                GAME_ID,
                                PLAY_ID,
                                play.getText());

        AiPlayTranslationResponse response =
                new AiPlayTranslationResponse(
                        null,
                        List.of(
                                "PLAYER_NAME_MISSING"),
                        false,
                        contextHash);

        when(playRepository.findById(PLAY_ID))
                .thenReturn(
                        Optional.of(play));

        when(aiServiceClient.generatePlayTranslation(any()))
                .thenReturn(
                        Optional.of(response));

        AiPlayTranslationGenerator.GenerationStatus status =
                generator.regenerateSynchronously(
                        GAME_ID,
                        PLAY_ID);

        assertThat(status)
                .isEqualTo(
                        AiPlayTranslationGenerator
                                .GenerationStatus
                                .REVIEW_REJECTED);

        assertThat(play.getTextKo())
                .isEqualTo(
                        "기존 번역");

        verify(liveSignalPublisher, never())
                .publishGameSignal(GAME_ID);
    }

    private void stubSuccessfulTranslation(
            Play play
    ) {
        String contextHash =
                AiContextHashCalculator
                        .calculatePlayTranslation(
                                GAME_ID,
                                PLAY_ID,
                                play.getText()
                        );

        AiPlayTranslationResponse response =
                new AiPlayTranslationResponse(
                        "Soto, 중견수 방면 안타",
                        List.of(),
                        false,
                        contextHash
                );

        when(playRepository.findById(PLAY_ID))
                .thenReturn(
                        Optional.of(play),
                        Optional.of(play)
                );

        when(aiServiceClient.generatePlayTranslation(any()))
                .thenReturn(Optional.of(response));
    }

    private static FinalHeadlineContext revealedContext(
            List<String> factTags
    ) {
        return new FinalHeadlineContext(
                GAME_ID,
                AiCopyMode.REVEALED,
                "STATUS_FINAL",
                "경기 종료",
                List.of(),
                List.of(),
                List.of(),

                null,
                null,
                "home",
                9,
                false,
                false,
                List.of(),

                null,
                null,
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(
                        new FinalHeadlineContext.VerifiedPlay(
                                PLAY_ID,
                                100L,
                                7,
                                "Bottom",
                                "Soto singled to center.",
                                "Soto, 중견수 방면 안타",
                                5,
                                3,
                                true,
                                1,
                                null,
                                null,
                                null,
                                null,
                                null,
                                false,
                                false,
                                false,
                                factTags
                        )
                ),

                "hash-final"
        );
    }

    private static Play play(
            String sourceText) {

        Play play =
                new Play();

        play.setId(PLAY_ID);
        play.setGameId(GAME_ID);
        play.setPlayOrder(100L);
        play.setType("Play Result");
        play.setInning(7);
        play.setInningType("Bottom");
        play.setBatterId(30L);
        play.setText(sourceText);

        return play;
    }
}
