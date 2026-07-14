import Card from '../../../shared/components/Card';
import TimelineItem from '../../../shared/components/TimelineItem';
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
   * 초·말은 공격팀을 추측하게 할 수 있으므로 이닝 숫자만 사용한다.
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
   * 이벤트가 없는 이닝의 빈 그룹은 생성하지 않는다.
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

function EventTimeline({
                         title = '이벤트 타임라인',
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
             * 카드 제목은 고정하고 목록 부분만 스크롤한다.
             * 목록이 최대 높이보다 짧으면 스크롤바는 나타나지 않는다.
             */
            <div className="max-h-[380px] overflow-y-auto overscroll-contain pr-2 sm:max-h-[430px] [scrollbar-gutter:stable]">
              <div className="flex flex-col gap-6">
                {groups.map((group) => (
                    <section key={group.key}>
                      <div className="flex items-center gap-3">
                  <span className="shrink-0 rounded-full bg-red-tint px-3 py-1 font-display text-xs font-bold text-mlb-red">
                    {group.label}
                  </span>

                        <div
                            aria-hidden="true"
                            className="h-px flex-1 bg-divider"
                        />
                      </div>

                      <ul className="mt-3 pl-1">
                        {group.events.map((event) => (
                            <TimelineItem
                                key={event.eventId}
                                text={event.text}
                                highlighted={
                                    event.highlighted ?? false
                                }
                            />
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