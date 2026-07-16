package com.pulse.api;

import com.pulse.api.GameRecentPlayQueryService.RecentPlaysResponse;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GameRecentPlayQueryServiceTest {

    private final GameRepository gameRepository =
            mock(GameRepository.class);

    private final PlayRepository playRepository =
            mock(PlayRepository.class);

    private final GameRecentPlayQueryService service =
            new GameRecentPlayQueryService(
                    gameRepository,
                    playRepository);

    @Test
    void protectedMode_shouldReturnEmptyWithoutReadingPlayData() {
        // given
        long gameId = 100L;

        when(gameRepository.existsById(gameId))
                .thenReturn(true);

        // when
        RecentPlaysResponse response =
                service.getRecentPlays(
                        gameId,
                        "PROTECTED");

        // then
        assertThat(response.plays())
                .isEmpty();

        /*
         * play 원문과 점수는 스포일러 데이터이므로
         * 보호 모드에서는 저장소 조회 자체를 하지 않는다.
         */
        verifyNoInteractions(playRepository);
    }

    @Test
    void invalidMode_shouldFallBackToProtectedMode() {
        // given
        long gameId = 200L;

        when(gameRepository.existsById(gameId))
                .thenReturn(true);

        // when
        RecentPlaysResponse response =
                service.getRecentPlays(
                        gameId,
                        "invalid-mode");

        // then
        assertThat(response.plays())
                .isEmpty();

        verifyNoInteractions(playRepository);
    }

    @Test
    void revealedMode_shouldReturnLatestTenPlateAppearanceResults() {
        // given
        long gameId = 300L;

        List<Play> source =
                new ArrayList<>();

        /*
         * 최신 데이터에 투구 로그와 투수 교체가 섞여 있어도
         * 실제 타석 결과만 최근 플레이로 사용해야 한다.
         */
        source.add(
                pitchLog(
                        900L,
                        gameId,
                        2000L));

        source.add(
                pitcherChange(
                        901L,
                        gameId,
                        1999L));

        source.add(
                blankResult(
                        902L,
                        gameId,
                        1998L));

        for (int index = 0; index < 12; index++) {
            source.add(
                    validResult(
                            1000L + index,
                            gameId,
                            1900L - index,
                            index));
        }

        when(gameRepository.existsById(gameId))
                .thenReturn(true);

        when(
                playRepository
                        .findByGameIdOrderByPlayOrderDesc(
                                eq(gameId),
                                any(Pageable.class)))
                .thenReturn(source);

        // when
        RecentPlaysResponse response =
                service.getRecentPlays(
                        gameId,
                        " normal ");

        // then
        assertThat(response.plays())
                .hasSize(10);

        assertThat(response.plays().get(0).playId())
                .isEqualTo(1000L);

        assertThat(response.plays().get(9).playId())
                .isEqualTo(1009L);

        assertThat(response.plays())
                .allSatisfy(
                        play -> {
                            assertThat(play.text())
                                    .startsWith("타석 결과 ");
                            assertThat(play.inningType())
                                    .isIn(
                                            "Top",
                                            "Bottom");
                            assertThat(play.score())
                                    .isNotNull();
                            /*
                             * 번역이 저장되지 않은 기존 play는
                             * 원문과 translated=false를 반환한다.
                             */
                            assertThat(play.translated())
                                    .isFalse();
                        });

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(playRepository)
                .findByGameIdOrderByPlayOrderDesc(
                        eq(gameId),
                        pageableCaptor.capture());

        /*
         * 필터링 후 10건을 확보하기 위해 최신 로그 200건을
         * 조회하도록 한 계약을 고정한다.
         */
        assertThat(pageableCaptor.getValue().getPageSize())
                .isEqualTo(200);
    }

    @Test
    void revealedMode_shouldPreferStoredTranslation() {
        // given
        long gameId = 400L;

        Play translatedPlay =
                translatedResult(
                        2000L,
                        gameId,
                        3000L);

        when(gameRepository.existsById(gameId))
                .thenReturn(true);

        when(
                playRepository
                        .findByGameIdOrderByPlayOrderDesc(
                                eq(gameId),
                                any(Pageable.class)))
                .thenReturn(
                        List.of(
                                translatedPlay));

        // when
        RecentPlaysResponse response =
                service.getRecentPlays(
                        gameId,
                        "REVEALED");

        // then
        assertThat(response.plays())
                .singleElement()
                .satisfies(
                        play -> {
                            /*
                             * Play에 한국어 번역문이 저장되어 있으면
                             * 원문 대신 저장된 번역문을 그대로 반환한다.
                             */
                            assertThat(play.text())
                                    .isEqualTo("타자가 안타를 기록했습니다.");
                            assertThat(play.translated())
                                    .isTrue();
                        });
    }

    @Test
    void missingGame_shouldReturnNotFound() {
        // given
        long gameId = 999L;

        when(gameRepository.existsById(gameId))
                .thenReturn(false);

        // when, then
        assertThatThrownBy(
                () ->
                        service.getRecentPlays(
                                gameId,
                                "REVEALED"))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(playRepository);
    }

    private static Play translatedResult(
            Long id,
            long gameId,
            long playOrder) {

        Play play =
                new Play();

        play.setId(id);
        play.setGameId(gameId);
        play.setPlayOrder(playOrder);
        play.setType("Play Result");
        play.setInning(7);
        play.setInningType("Bottom");
        play.setText(" Batter singles to center field. ");
        play.setTextKo(" 타자가 안타를 기록했습니다. ");
        play.setBatterId(100L);
        play.setPitcherId(200L);
        play.setHomeScore(2);
        play.setAwayScore(1);
        play.setFetchedAt(
                Instant.parse("2026-07-16T09:00:00Z"));

        return play;
    }

    private static Play pitchLog(
            Long id,
            long gameId,
            long playOrder) {

        Play play =
                basePlay(
                        id,
                        gameId,
                        playOrder);

        play.setType("Pitch");
        play.setText("투구 로그");
        play.setBatterId(10L);

        return play;
    }

    private static Play pitcherChange(
            Long id,
            long gameId,
            long playOrder) {

        Play play =
                basePlay(
                        id,
                        gameId,
                        playOrder);

        play.setType("Play Result");
        play.setText("투수 교체");
        play.setBatterId(null);

        return play;
    }

    private static Play blankResult(
            Long id,
            long gameId,
            long playOrder) {

        Play play =
                basePlay(
                        id,
                        gameId,
                        playOrder);

        play.setType("Play Result");
        play.setText("   ");
        play.setBatterId(10L);

        return play;
    }

    private static Play validResult(
            Long id,
            long gameId,
            long playOrder,
            int index) {

        Play play =
                basePlay(
                        id,
                        gameId,
                        playOrder);

        play.setType("Play Result");
        play.setInning(9 - index / 2);
        play.setInningType(
                index % 2 == 0
                        ? "Top"
                        : "Bottom");
        play.setText(
                " 타석 결과 " + index + " ");
        play.setBatterId(
                100L + index);
        play.setPitcherId(
                200L + index);
        play.setHomeScore(index);
        play.setAwayScore(index + 1);
        play.setFetchedAt(
                Instant.parse("2026-07-14T12:00:00Z")
                        .minusSeconds(index));

        return play;
    }

    private static Play basePlay(
            Long id,
            long gameId,
            long playOrder) {

        Play play =
                new Play();

        play.setId(id);
        play.setGameId(gameId);
        play.setPlayOrder(playOrder);
        play.setInning(9);
        play.setInningType("Top");
        play.setFetchedAt(
                Instant.parse("2026-07-14T12:00:00Z"));

        return play;
    }
}
