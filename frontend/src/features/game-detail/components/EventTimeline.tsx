import Card from '../../../shared/components/Card';
import TimelineItem from '../../../shared/components/TimelineItem';
import type { DisplayMode } from '../lib/displayMode';

export interface TimelineEvent {
  eventId: number;
  inning: number;

  /**
   * 보호 응답에서는 초·말이 없을 수 있다.
   * 공개 모드에서만 이 값을 화면에 사용한다.
   */
  inningType?: 'TOP' | 'BOTTOM';

  /**
   * 표시 문구는 서버의 copy를 우선 사용하고,
   * copy가 없을 때 label을 폴백한 최종 문자열이다.
   */
  text: string;

  /** 관심 선수 이벤트를 별도 카드가 아닌 인라인 별표로 강조한다. */
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

function getGroupKey(event: TimelineEvent, mode: DisplayMode) {
  /**
   * 보호 모드에서는 초·말을 기준으로 그룹을 나누는 것 자체가
   * 공격팀 방향을 추측하게 할 수 있으므로 숫자 이닝만 사용한다.
   */
  if (mode === 'PROTECTED') {
    return String(event.inning);
  }

  return `${event.inning}-${event.inningType ?? 'UNKNOWN'}`;
}

function getGroupLabel(event: TimelineEvent, mode: DisplayMode) {
  if (mode === 'PROTECTED' || !event.inningType) {
    return `${event.inning}회`;
  }

  return `${event.inning}회 ${event.inningType === 'TOP' ? '초' : '말'}`;
}

function groupEventsByInning(
    events: TimelineEvent[],
    mode: DisplayMode,
): TimelineGroup[] {
  const groups = new Map<string, TimelineGroup>();

  /**
   * 서버가 전달한 시간 순서를 그대로 유지한다.
   * 데이터에 없는 이닝을 인위적으로 생성하지 않기 때문에
   * 이벤트가 없는 이닝의 빈 그룹은 화면에 나타나지 않는다.
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
  const groups = groupEventsByInning(events, mode);

  return (
      <Card>
        <h3 className="mb-5 text-[15px] font-bold text-text-strong">
          {title}
        </h3>

        {groups.length === 0 ? (
            <p className="py-2 text-sm text-text-muted">
              아직 기록된 이벤트가 없습니다.
            </p>
        ) : (
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
                              highlighted={event.highlighted ?? false}
                          />
                      ))}
                    </ul>
                  </section>
              ))}
            </div>
        )}
      </Card>
  );
}

export default EventTimeline;