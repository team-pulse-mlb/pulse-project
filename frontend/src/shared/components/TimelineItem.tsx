interface TimelineItemProps {
  /**
   * 표시 문구는 서버의 AI 문구(copy)를 우선 사용하고,
   * copy가 없을 때만 label을 전달한다.
   */
  text: string;

  /** 관심 선수 등장 시 문구 앞에 인라인 별표만 표시한다. */
  highlighted?: boolean;
}

/**
 * 이닝 그룹 안에 표시되는 이벤트 한 줄이다.
 *
 * 이닝 정보는 EventTimeline의 그룹 헤더에서 한 번만 표시하므로
 * 개별 이벤트 행에서는 반복하지 않는다.
 */
function TimelineItem({
  text,
  highlighted = false,
}: TimelineItemProps) {
  return (
    <li className="flex items-start gap-3 border-t border-divider py-3.5 first:border-t-0 first:pt-0 last:pb-0">
      <span
        aria-hidden="true"
        className="mt-[9px] h-2 w-2 shrink-0 rounded-full bg-mlb-red"
      />

      <p className="min-w-0 text-[15px] leading-relaxed text-text-body">
        {highlighted && (
          <span aria-label="관심 선수" className="mr-1 text-gold">
            ★
          </span>
        )}

        {text}
      </p>
    </li>
  );
}

export default TimelineItem;
