package com.pulse.ai;

import com.pulse.common.ai.AiCopyContextReader;
import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.AiCopyResult;
import com.pulse.common.ai.FinalHeadlineContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiFinalHeadlineCopyClientTest {

    @Test
    void generateFinalHeadlineShouldCarryEvidenceIntoCommonResult() {
        AiCopyContextReader contextReader = mock(
                AiCopyContextReader.class
        );
        AiServiceClient aiServiceClient = mock(
                AiServiceClient.class
        );
        AiFinalHeadlineContextMapper contextMapper =
                new AiFinalHeadlineContextMapper();

        AiFinalHeadlineCopyClient client =
                new AiFinalHeadlineCopyClient(
                        contextReader,
                        contextMapper,
                        aiServiceClient
                );

        FinalHeadlineContext context = new FinalHeadlineContext(
                5059082L,
                AiCopyMode.REVEALED,
                "STATUS_FINAL",
                "경기 종료",
                List.of(),
                List.of(),
                List.of(),
                new FinalHeadlineContext.FinalScore(5, 3),
                "home",
                "game-5059082-final-headline-v1"
        );

        AiFinalHeadlineRequest request =
                contextMapper.toRequest(context);

        AiCopyResponse response = new AiCopyResponse(
                true,
                "game-5059082-final-headline-v1",
                "홈팀이 5-3으로 승리",
                List.of(),
                false,
                List.of(
                        "summaryFacts.winnerSide",
                        "summaryFacts.winnerScore",
                        "summaryFacts.loserScore"
                ),
                List.of(312L)
        );

        when(
                contextReader.finalHeadlineContext(
                        5059082L,
                        AiCopyMode.REVEALED
                )
        ).thenReturn(Optional.of(context));

        when(
                aiServiceClient.generateFinalHeadline(request)
        ).thenReturn(Optional.of(response));

        AiCopyResult result = client.generateFinalHeadline(
                5059082L,
                AiCopyMode.REVEALED
        ).orElseThrow();

        assertThat(result.spoilerSafe()).isTrue();
        assertThat(result.contextHash())
                .isEqualTo("game-5059082-final-headline-v1");
        assertThat(result.safeTitle())
                .isEqualTo("홈팀이 5-3으로 승리");

        assertThat(result.usedFactIds()).containsExactly(
                "summaryFacts.winnerSide",
                "summaryFacts.winnerScore",
                "summaryFacts.loserScore"
        );
        assertThat(result.usedPlayIds())
                .containsExactly(312L);

        verify(aiServiceClient)
                .generateFinalHeadline(request);
    }
}
