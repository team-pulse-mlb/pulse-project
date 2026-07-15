import Card from '../../../shared/components/Card';
import type { DisplayMode } from '../lib/displayMode';

export interface TimelineEvent {
  eventId: number;
  inning: number;

  /**
   * 보호 응답에는 초·말이 포함되지 않는다.
   * 공개 모드에서만 그룹 제목에 사용한다.
   */
  inningType?: 'TOP' | 'BOTTOM';

  /**
   * 서버의 AI 문구(copy)를 우선 사용하고,
   * copy가 없을 때 보호·공개 라벨을 폴백한 최종 문구다.
   */
  text: string;

  /** 공개 모드에서 관심 선수 관련 이벤트를 강조할 때 사용한다. */
  highlighted?: boolean;
}

interface EventTimelineProps {
  title?: string;
  mode: DisplayMode;
  events: TimelineEvent[];
}

interface TimelineGroup {
  key: string;
  label: string;
  events: TimelineEvent[];
}

function getGroupKey(
    event: TimelineEvent,
    mode: DisplayMode,
) {
  /**
   * 보호 모드에서는 초·말을 기준으로 나누지 않는다.
   * 공격 팀을 유추할 수 있으므로 이닝 숫자만 사용한다.
   */
  if (mode === 'PROTECTED') {
    return String(event.inning);
  }

  return `${event.inning}-${event.inningType ?? 'UNKNOWN'}`;
}

function getGroupLabel(
    event: TimelineEvent,
    mode: DisplayMode,
) {
  /**
   * 보호 모드는 이닝 숫자만 보여준다.
   */
  if (
      mode === 'PROTECTED' ||
      !event.inningType
  ) {
    return `${event.inning}회`;
  }

  return `${event.inning}회 ${
      event.inningType === 'TOP' ? '초' : '말'
  }`;
}

function groupEventsByInning(
    events: TimelineEvent[],
    mode: DisplayMode,
): TimelineGroup[] {
  const groups = new Map<string, TimelineGroup>();

  /**
   * 서버가 전달한 이벤트 순서를 그대로 유지한다.
   * 종료 경기는 오래된 순서,
   * 진행 경기는 GameDetailPage에서 뒤집은 최신순이 들어온다.
   */
  events.forEach((event) => {
    const key = getGroupKey(event, mode);
    const existingGroup = groups.get(key);

    if (existingGroup) {
      existingGroup.events.push(event);
      return;
    }

    groups.set(key, {
      key,
      label: getGroupLabel(event, mode),
      events: [event],
    });
  });

  return Array.from(groups.values());
}

/**
 * 보호 모드 경기 흐름을 이닝별 박스로 묶어 표시한다.
 *
 * 보호 모드에서는 초·말과 점수를 드러내지 않고,
 * 각 문장은 레드 점 + 텍스트의 한 줄 구조로 표시한다.
 */
function EventTimeline({
                         title = '경기 흐름',
                         mode,
                         events,
                       }: EventTimelineProps) {
  const groups = groupEventsByInning(
      events,
      mode,
  );

  return (
      <Card className="overflow-hidden">
        <h3 className="mb-5 text-[15px] font-bold text-text-strong">
          {title}
        </h3>

        {groups.length === 0 ? (
            <p className="py-2 text-sm text-text-muted">
              아직 기록된 이벤트가 없습니다.
            </p>
        ) : (
            /**
             * 카드 전체가 길어지는 것을 막기 위해
             * 목록 영역만 세로 스크롤한다.
             */
            <div className="max-h-[380px] overflow-y-auto overscroll-contain pr-2 sm:max-h-[430px] [scrollbar-gutter:stable]">
              <div className="flex flex-col gap-4">
                {groups.map((group) => (
                    <section
                        key={group.key}
                        className="rounded-xl border border-divider px-4 py-3.5"
                    >
                      {/*
                 * 보호 모드 이닝 배지는 MLB 네이비 배경으로 강조한다.
                 * 보호 모드이므로 초·말 없이 n회만 표시한다.
                 */}
                      <div>
                  <span className="inline-flex rounded-full bg-mlb-navy px-3.5 py-1.5 font-display text-[13px] font-bold text-white">
                    {group.label}
                  </span>
                      </div>

                      <ul className="mt-3 space-y-[10px]">
                        {group.events.map((event) => (
                            <li
                                key={event.eventId}
                                className="flex items-center gap-3"
                            >
                              {/*
                       * 보호 모드 문장 왼쪽 점은 레드로 표시한다.
                       * 스코어는 보호 정책상 출력하지 않는다.
                       */}
                              <span
                                  aria-hidden="true"
                                  className="h-2 w-2 shrink-0 rounded-full bg-mlb-red"
                              />

                              <p className="min-w-0 text-[15px] leading-relaxed text-text-body">
                                {event.highlighted && (
                                    <span
                                        aria-label="관심 선수"
                                        className="mr-1 text-gold"
                                    >
                            ★
                          </span>
                                )}

                                {event.text}
                              </p>
                            </li>
                        ))}
                      </ul>
                    </section>
                ))}
              </div>
            </div>
        )}
      </Card>
  );
}

export default EventTimeline;