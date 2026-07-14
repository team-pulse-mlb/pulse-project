// 시각·이닝 표기 공용 포맷터.
// 스포일러 정책: 이닝 숫자는 표기하되 초/말(inningType)은 공개 모드 데이터로만 조립한다.

const timeFormatter = new Intl.DateTimeFormat('ko-KR', {
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

/** ISO 시각 → "7/9 08:05" (사용자 로컬 시간대, USER_FLOW §3.2 배지 형식) */
export function formatStartTime(isoTime: string): string {
  const date = new Date(isoTime);
  return `${date.getMonth() + 1}/${date.getDate()} ${timeFormatter.format(date)}`;
}

const dateLabelFormatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'long',
  day: 'numeric',
  weekday: 'short',
});

/** "YYYY-MM-DD" → "7월 10일 (금)" */
export function formatSlateDateLabel(slateDate: string): string {
  return dateLabelFormatter.format(new Date(`${slateDate}T00:00:00`));
}

/** "YYYY-MM-DD" 문자열에 일 단위 가감 */
export function shiftDate(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00`);
  d.setDate(d.getDate() + days);

  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

const slateDateFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'America/New_York',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

/** 미 동부시간 기준 오늘 슬레이트 날짜 "YYYY-MM-DD" (서버 SlateZone과 동일 기준) */
export function todaySlateDate(): string {
  return slateDateFormatter.format(new Date());
}

/** 이닝 숫자 → "5회" (초/말 없음, 보호 안전) */
export function formatInning(inning: number | null | undefined): string | null {
  if (inning == null) {
    return null;
  }
  return `${inning}회`;
}
