import type { ApiInningType } from './gameDetailTypes';

/**
 * 공개 이벤트에서 사용하는 관련 선수 정보다.
 *
 * 선수 이름이 수집되지 않았으면 null일 수 있다.
 */
export interface EventPlayersResponse {
    batter: string | null;
    pitcher: string | null;
}

/**
 * 이벤트 유형별로 공개가 허용된 근거 수치다.
 *
 * 백엔드는 game_events.payload 전체를 반환하지 않고,
 * 이벤트 정책에서 허용한 숫자·불리언 값만 반환한다.
 */
export type EventEvidenceResponse =
    Record<string, number | boolean>;

/**
 * 보호·공개 이벤트가 공통으로 가지는 필드다.
 */
interface BaseGameEventResponse {
    eventId: number;
    eventType: string;

    /**
     * 이닝 정보가 없는 잘못된 이벤트도 API에 존재할 수 있으므로
     * 프론트 매퍼에서 null 이벤트를 제외한다.
     */
    inning: number | null;

    label: string | null;
    copy: string | null;
    observedAt: string | null;
}

/**
 * 보호 모드 이벤트 응답이다.
 *
 * 스포일러 보호를 위해 다음 필드는 존재하지 않는다.
 * - inningType
 * - players
 * - evidence
 */
export type ProtectedGameEventResponse =
    BaseGameEventResponse;

/**
 * 공개 모드 이벤트 응답이다.
 */
export interface RevealedGameEventResponse
    extends BaseGameEventResponse {
    inningType: ApiInningType | null;
    players: EventPlayersResponse;
    evidence: EventEvidenceResponse;
}

/**
 * 보호·공개 이벤트 응답의 합집합 타입이다.
 *
 * 매퍼에서는 'inningType' in event를 이용해
 * 공개 이벤트 응답인지 안전하게 판별한다.
 */
export type GameEventResponse =
    | ProtectedGameEventResponse
    | RevealedGameEventResponse;

/**
 * GET /api/games/{gameId}/events 응답이다.
 */
export interface GameEventsResponse {
    events: GameEventResponse[];
}