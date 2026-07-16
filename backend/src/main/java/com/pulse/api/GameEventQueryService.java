package com.pulse.api;

import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventLabelPolicy;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * 경기 상세 화면의 보호 모드 경기 흐름을 조회한다.
 *
 * 보호 모드:
 * - 스포일러 안전 이벤트만 반환한다.
 * - 초·말, 선수 이름, 근거 수치, 공개 문구를 노출하지 않는다.
 *
 * 공개 모드:
 * - 최근 플레이 API를 경기 흐름의 단일 원천으로 사용한다.
 * - 이벤트 API에서는 빈 목록을 반환한다.
 */
@Service
@RequiredArgsConstructor
public class GameEventQueryService {

    private final GameRepository gameRepository;
    private final GameEventRepository gameEventRepository;

    /**
     * 경기별 보호 안전 이벤트 목록을 반환한다.
     *
     * mode가 없거나 잘못된 값이면 보호 모드로 처리한다.
     * 공개 모드는 최근 플레이 API를 단일 원천으로 사용하므로
     * 이벤트 API에서는 빈 목록을 반환한다.
     */
    public GameEventsResponse getEvents(
            long gameId,
            String mode) {

        if (!gameRepository.existsById(gameId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "경기를 찾을 수 없습니다.");
        }

        /*
         * 공개 모드의 경기 흐름은 최근 플레이 API가 담당한다.
         * 공개 이벤트 문구나 선수 정보가 다시 노출되지 않도록
         * 이벤트 저장소를 조회하지 않고 빈 목록을 반환한다.
         */
        if (isRevealed(mode)) {
            return new GameEventsResponse(List.of());
        }

        List<ProtectedEventResponse> responses =
                gameEventRepository
                        .findByGameIdAndSpoilerLevelAndTimelineHighlightTrueOrderByObservedAtAscIdAsc(
                                gameId,
                                GameEvent.SPOILER_PROTECTED_SAFE)
                        .stream()
                        /*
                         * 정책에 등록되지 않은 이벤트 유형은
                         * 기본 차단하여 응답에서 제외한다.
                         */
                        .filter(
                                event ->
                                        protectedLabel(event) != null)
                        .map(
                                GameEventQueryService
                                        ::toProtectedResponse)
                        .toList();

        return new GameEventsResponse(responses);
    }

    /**
     * 보호 안전 이벤트를 외부 응답으로 변환한다.
     *
     * 초·말, 선수 이름, 근거 수치와 공개 문구는 포함하지 않는다.
     */
    private static ProtectedEventResponse toProtectedResponse(
            GameEvent event) {

        return new ProtectedEventResponse(
                event.getId(),
                event.getEventType(),
                event.getInning(),
                protectedLabel(event),
                nullableText(event.getCopyProtected()),
                event.getObservedAt());
    }

    /**
     * 보호 정책에 등록된 이벤트 라벨을 조회한다.
     *
     * 등록되지 않은 이벤트 유형은 null을 반환해 기본 차단한다.
     */
    private static String protectedLabel(
            GameEvent event) {

        return GameEventLabelPolicy.protectedLabel(
                event.getSpoilerLevel(),
                event.getEventType());
    }

    /**
     * REVEALED와 기존 호환용 NORMAL만 공개 모드로 인정한다.
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

    /**
     * 빈 문자열을 null로 정규화한다.
     */
    private static String nullableText(
            String value) {

        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    /**
     * 이벤트 API의 최상위 응답이다.
     */
    public record GameEventsResponse(
            List<ProtectedEventResponse> events) {
    }

    /**
     * 보호 모드 이벤트 응답이다.
     *
     * 초·말, 선수 이름, 근거 수치는 포함하지 않는다.
     */
    public record ProtectedEventResponse(
            Long eventId,
            String eventType,
            Integer inning,
            String label,
            String copy,
            Instant observedAt) {
    }
}
