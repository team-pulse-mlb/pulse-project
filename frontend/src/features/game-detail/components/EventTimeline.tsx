import Card from '../../../shared/components/Card';
import TimelineItem from '../../../shared/components/TimelineItem';

// 이벤트 타임라인 (진행·종료 공용).
// 문구는 AI 문구(copy) 우선, 없으면 label 폴백 — 프론트에서 태그→문구 조립 금지.
export interface TimelineEvent {
  eventId: number;
  inningLabel: string;
  text: string;
  /** 관심 선수 등장 시 인라인 ★ 강조 */
  highlighted?: boolean;
}

interface EventTimelineProps {
  title?: string;
  events: TimelineEvent[];
}

function EventTimeline({ title = '이벤트 타임라인', events }: EventTimelineProps) {
  return (
    <Card>
      <h3 className="mb-4 text-[15px] font-bold text-text-strong">{title}</h3>

      {events.length === 0 ? (
        <p className="py-2 text-sm text-text-muted">
          아직 기록된 이벤트가 없습니다.
        </p>
      ) : (
        <ul>
          {events.map((event) => (
            <TimelineItem
              key={event.eventId}
              inningLabel={event.inningLabel}
              text={event.text}
              highlighted={event.highlighted}
            />
          ))}
        </ul>
      )}
    </Card>
  );
}

export default EventTimeline;
