interface TimelineItemProps {
  /** 이닝 표기 (보호 모드는 "5회", 공개 모드는 "5회 말" 형태로 조립해서 전달) */
  inningLabel: string;
  /** 표시 문구: AI 문구(copy) 우선, 없으면 label 폴백 — 조립은 호출부 책임 */
  text: string;
  /** 관심 선수 등장 시 인라인 ★ 강조 (별도 박스 금지) */
  highlighted?: boolean;
}

// 이벤트 타임라인 한 줄 (진행·종료 상세 공용).
function TimelineItem({ inningLabel, text, highlighted = false }: TimelineItemProps) {
  return (
    <li className="flex items-start gap-3 border-t border-divider py-3.5 first:border-t-0 first:pt-0 last:pb-0">
      <span className="mt-0.5 shrink-0 rounded-full bg-red-tint px-2.5 py-1 font-display text-xs font-bold text-mlb-red">
        {inningLabel}
      </span>
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
