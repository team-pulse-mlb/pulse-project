package com.pulse.api;

import com.pulse.domain.GameEvent;
import com.pulse.domain.GameEventLabelPolicy;
import com.pulse.domain.GameEventRepository;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Player;
import com.pulse.domain.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * 경기 상세 화면의 이벤트 타임라인을 조회한다.
 *
 * 보호 모드:
 * - 스포일러 안전 이벤트만 반환한다.
 * - 초/말, 선수 이름, 근거 수치를 노출하지 않는다.
 *
 * 공개 모드:
 * - 보호 이벤트와 공개 전용 이벤트를 모두 반환한다.
 * - 초/말, 관련 선수, 허용된 근거 수치를 반환한다.
 */
@Service
@RequiredArgsConstructor
public class GameEventQueryService {

    private final GameRepository gameRepository;
    private final GameEventRepository gameEventRepository;
    private final PlayerRepository playerRepository;

    /**
     * 경기별 이벤트 목록을 표시 모드에 맞게 반환한다.
     *
     * mode가 없거나 잘못된 값이면 보호 모드로 처리한다.
     */
    public GameEventsResponse getEvents(
            long gameId,
            String mode) {

        if (!gameRepository.existsById(gameId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "경기를 찾을 수 없습니다.");
        }

        boolean revealed =
                isRevealed(mode);

        /*
         * 보호 모드와 공개 모드 모두
         * PROTECTED_SAFE 이벤트를 기본으로 포함한다.
         */
        List<GameEvent> events =
                new ArrayList<>(
                        gameEventRepository
                                .findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                                        gameId,
                                        GameEvent.SPOILER_PROTECTED_SAFE));

        /*
         * 득점, 홈런, 리드 교체처럼 결과 방향을 드러내는 이벤트는
         * 공개 모드에서만 추가한다.
         */
        if (revealed) {
            events.addAll(
                    gameEventRepository
                            .findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(
                                    gameId,
                                    GameEvent.SPOILER_REVEALED_ONLY));
        }

        /*
         * 두 종류의 이벤트 목록을 합쳤기 때문에
         * 전체 발생 시간 순서로 다시 정렬한다.
         */
        events.sort(
                Comparator
                        .comparing(
                                GameEvent::getObservedAt,
                                Comparator.nullsLast(
                                        Comparator.naturalOrder()))
                        .thenComparing(
                                GameEvent::getId,
                                Comparator.nullsLast(
                                        Comparator.naturalOrder())));

        /*
         * 선수 이름은 공개 모드에서만 필요하다.
         * 보호 모드에서는 선수 조회 자체를 하지 않는다.
         */
        Map<Long, String> playerNames =
                revealed
                        ? loadPlayerNames(events)
                        : Map.of();

        List<GameEventView> responses =
                events.stream()
                        /*
                         * 정책에 등록되지 않은 이벤트는
                         * 기본 허용하지 않고 응답에서 제외한다.
                         */
                        .filter(
                                event ->
                                        eventLabel(
                                                event,
                                                revealed)
                                                != null)
                        .map(
                                event ->
                                        toResponse(
                                                event,
                                                revealed,
                                                playerNames))
                        .toList();

        return new GameEventsResponse(
                responses);
    }

    /**
     * 도메인 이벤트를 보호 또는 공개 응답 DTO로 변환한다.
     */
    private static GameEventView toResponse(
            GameEvent event,
            boolean revealed,
            Map<Long, String> playerNames) {

        String label =
                eventLabel(
                        event,
                        revealed);

        if (!revealed) {
            return new ProtectedEventResponse(
                    event.getId(),
                    event.getEventType(),
                    event.getInning(),
                    label,
                    nullableText(
                            event.getCopyProtected()),
                    event.getObservedAt());
        }

        /*
         * payload 전체를 공개하지 않는다.
         *
         * 기존 AI 문구 생성과 동일한 허용 목록을 이용해
         * 이벤트 유형별로 안전한 숫자·불리언 근거만 반환한다.
         */
        Map<String, Object> evidence =
                AiCopyContextService.projectEvidence(
                        event.getEventType(),
                        event.getPayload());

        return new RevealedEventResponse(
                event.getId(),
                event.getEventType(),
                event.getInning(),
                nullableText(
                        event.getInningType()),
                label,
                nullableText(
                        event.getCopyRevealed()),
                new EventPlayersResponse(
                        findPlayerName(
                                playerNames,
                                event.getBatterId()),
                        findPlayerName(
                                playerNames,
                                event.getPitcherId())),
                evidence,
                event.getObservedAt());
    }

    /**
     * 선수 ID가 없는 보호 안전 이벤트도
     * 공개 모드 응답에 포함될 수 있으므로 null을 안전하게 처리한다.
     */
    private static String findPlayerName(
            Map<Long, String> playerNames,
            Long playerId) {

        if (playerId == null) {
            return null;
        }

        return playerNames.get(
                playerId);
    }

    /**
     * 표시 모드에 맞는 이벤트 라벨을 조회한다.
     *
     * 정책에 등록되지 않은 이벤트는 null이 반환된다.
     */
    private static String eventLabel(
            GameEvent event,
            boolean revealed) {

        return revealed
                ? GameEventLabelPolicy.revealedLabel(
                event.getSpoilerLevel(),
                event.getEventType())
                : GameEventLabelPolicy.protectedLabel(
                event.getSpoilerLevel(),
                event.getEventType());
    }

    /**
     * 공개 응답에 필요한 선수 이름을 한 번에 조회한다.
     *
     * 이벤트마다 findById를 호출하지 않아
     * N+1 조회가 발생하지 않도록 한다.
     */
    private Map<Long, String> loadPlayerNames(
            List<GameEvent> events) {

        Set<Long> playerIds =
                new HashSet<>();

        for (GameEvent event : events) {
            if (event.getBatterId() != null) {
                playerIds.add(
                        event.getBatterId());
            }

            if (event.getPitcherId() != null) {
                playerIds.add(
                        event.getPitcherId());
            }
        }

        if (playerIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> names =
                new HashMap<>();

        for (
                Player player
                : playerRepository.findAllById(
                playerIds)
        ) {
            String name =
                    playerName(player);

            if (name != null) {
                names.put(
                        player.getId(),
                        name);
            }
        }

        return Map.copyOf(names);
    }

    /**
     * fullName을 우선 사용하고,
     * 없으면 firstName과 lastName을 조합한다.
     */
    private static String playerName(
            Player player) {

        String fullName =
                nullableText(
                        player.getFullName());

        if (fullName != null) {
            return fullName;
        }

        String firstName =
                nullableText(
                        player.getFirstName());

        String lastName =
                nullableText(
                        player.getLastName());

        String combined =
                String.join(
                                " ",
                                firstName == null
                                        ? ""
                                        : firstName,
                                lastName == null
                                        ? ""
                                        : lastName)
                        .trim();

        return combined.isEmpty()
                ? null
                : combined;
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
            List<GameEventView> events) {
    }

    /**
     * 보호·공개 이벤트 응답의 공통 타입이다.
     */
    public sealed interface GameEventView
            permits ProtectedEventResponse,
            RevealedEventResponse {
    }

    /**
     * 보호 모드 이벤트 응답이다.
     *
     * 초/말, 선수 이름, 근거 수치는 포함하지 않는다.
     */
    public record ProtectedEventResponse(
            Long eventId,
            String eventType,
            Integer inning,
            String label,
            String copy,
            Instant observedAt)
            implements GameEventView {
    }

    /**
     * 공개 모드 이벤트 응답이다.
     */
    public record RevealedEventResponse(
            Long eventId,
            String eventType,
            Integer inning,
            String inningType,
            String label,
            String copy,
            EventPlayersResponse players,
            Map<String, Object> evidence,
            Instant observedAt)
            implements GameEventView {
    }

    /**
     * 이벤트와 관련된 타자·투수 정보다.
     */
    public record EventPlayersResponse(
            String batter,
            String pitcher) {
    }
}