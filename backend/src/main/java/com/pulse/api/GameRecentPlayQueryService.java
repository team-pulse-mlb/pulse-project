package com.pulse.api;

import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * 경기 상세 화면의 최근 플레이 목록을 조회한다.
 *
 * 최근 플레이에는 이닝 초/말, 점수, 실제 play 문구가 포함되므로
 * 공개 모드에서만 데이터를 반환한다.
 */
@Service
@RequiredArgsConstructor
public class GameRecentPlayQueryService {

    private static final String PLAY_RESULT_TYPE = "Play Result";

    private final GameRepository gameRepository;
    private final PlayRepository playRepository;

    /**
     * 공개 모드에서만 최근 플레이를 반환한다.
     *
     * mode가 없거나 잘못된 값이면 보호 모드로 처리하며,
     * 스포일러가 포함된 play 데이터를 반환하지 않는다.
     */
    public RecentPlaysResponse getRecentPlays(
            long gameId,
            String mode) {

        if (!gameRepository.existsById(gameId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "경기를 찾을 수 없습니다.");
        }

        /*
         * 보호 모드에서는 최근 play 원문과 점수를
         * 응답에 포함하지 않는다.
         */
        if (!isRevealed(mode)) {
            return new RecentPlaysResponse(
                    List.of());
        }

        List<RecentPlayResponse> plays =
                playRepository
                        .findByGameIdAndTypeIgnoreCaseOrderByPlayOrderDesc(
                                gameId,
                                PLAY_RESULT_TYPE)
                        .stream()
                        /*
                         * 화면에 표시할 수 없는 불완전한 play는
                         * 임의로 값을 만들어내지 않고 제외한다.
                         */
                        .filter(
                                GameRecentPlayQueryService
                                        ::hasDisplayableContent)
                        .map(
                                GameRecentPlayQueryService
                                        ::toResponse)
                        .toList();

        return new RecentPlaysResponse(
                plays);
    }

    /**
     * 최근 플레이 화면에 필요한 필수값을 검사한다.
     *
     * 투수 교체처럼 타자가 없는 Play Result는 제외하고,
     * 실제 타석 결과만 최근 플레이로 사용한다.
     */
    private static boolean hasDisplayableContent(
            Play play) {

        return play.getId() != null
                && play.getBatterId() != null
                && play.getInning() != null
                && play.getInning() > 0
                && isSupportedInningType(
                play.getInningType())
                && hasText(
                play.getText());
    }

    /**
     * Play 엔티티를 공개 모드 최근 플레이 응답으로 변환한다.
     */
    private static RecentPlayResponse toResponse(
            Play play) {

        /*
         * 저장된 한국어 번역문이 있으면 화면 표시 문구로 우선 사용하고,
         * 번역이 아직 없으면 기존 원문으로 폴백한다.
         */
        String translatedText =
                play.getTextKo();

        boolean translated =
                hasText(
                        translatedText);

        String displayText =
                translated
                        ? translatedText.trim()
                        : play.getText().trim();

        return new RecentPlayResponse(
                play.getId(),
                play.getInning(),
                play.getInningType(),
                displayText,
                translated,
                new RecentPlayScoreResponse(
                        play.getHomeScore(),
                        play.getAwayScore()),
                play.getFetchedAt());
    }

    /**
     * 기존 클라이언트 호환용 NORMAL도 공개 모드로 인정한다.
     *
     * 나머지 값은 모두 보호 모드로 처리한다.
     */
    private static boolean isRevealed(
            String mode) {

        if (mode == null) {
            return false;
        }

        String normalized =
                mode.trim()
                        .toUpperCase();

        return "REVEALED".equals(normalized)
                || "NORMAL".equals(normalized);
    }

    private static boolean isSupportedInningType(
            String inningType) {

        return "Top".equalsIgnoreCase(
                inningType)
                || "Bottom".equalsIgnoreCase(
                inningType);
    }

    private static boolean hasText(
            String value) {

        return value != null
                && !value.isBlank();
    }

    /**
     * 최근 플레이 API의 최상위 응답이다.
     */
    public record RecentPlaysResponse(
            List<RecentPlayResponse> plays) {
    }

    /**
     * 공개 모드에서 표시할 개별 최근 플레이다.
     */
    public record RecentPlayResponse(
            Long playId,
            Integer inning,
            String inningType,
            String text,
            boolean translated,
            RecentPlayScoreResponse score,
            Instant observedAt) {
    }

    /**
     * 해당 play 시점의 홈·원정 점수다.
     *
     * 수집되지 않은 점수는 null로 유지하며
     * 서버에서 가짜 점수를 만들지 않는다.
     */
    public record RecentPlayScoreResponse(
            Integer home,
            Integer away) {
    }
}