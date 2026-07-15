import type { TimelineEvent } from '../components/EventTimeline';
import type {
    GameEventResponse,
    GameEventsResponse,
} from '../api/gameEventTypes';

/**
 * 백엔드의 Top/Bottom 값을
 * EventTimeline이 사용하는 TOP/BOTTOM으로 변환한다.
 */
function normalizeInningType(
    event: GameEventResponse,
): 'TOP' | 'BOTTOM' | null {
    /*
     * 보호 응답에는 inningType 키 자체가 없다.
     */
    if (!('inningType' in event)) {
        return null;
    }

    if (event.inningType === 'Top') {
        return 'TOP';
    }

    if (event.inningType === 'Bottom') {
        return 'BOTTOM';
    }

    return null;
}

/**
 * AI 문구가 있으면 우선 사용하고,
 * 문구가 없으면 정책 라벨을 대신 사용한다.
 */
function eventText(
    event: GameEventResponse,
): string | null {
    const copy =
        event.copy?.trim()
        || null;

    if (copy !== null) {
        return copy;
    }

    return event.label?.trim()
        || null;
}

/**
 * 이벤트 API 응답을 EventTimeline 화면 모델로 변환한다.
 *
 * 이닝이나 출력 문구가 없는 잘못된 이벤트는
 * 임의의 값을 생성하지 않고 화면 목록에서 제외한다.
 */
export function toTimelineEvents(
    response: GameEventsResponse,
): TimelineEvent[] {
    return response.events.flatMap(
        (event) => {
            const text =
                eventText(event);

            if (
                event.inning === null
                || event.inning < 1
                || text === null
            ) {
                return [];
            }

            const inningType =
                normalizeInningType(event);

            return [
                {
                    eventId: event.eventId,
                    inning: event.inning,

                    ...(inningType !== null
                        ? {
                            inningType,
                        }
                        : {}),

                    text,

                    /*
                     * 관심 선수 강조는 별도 사용자 선호 연결 단계에서
                     * 실제 판단할 수 있으므로 현재는 임의 강조하지 않는다.
                     */
                    highlighted: false,
                },
            ];
        },
    );
}