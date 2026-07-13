export interface BoxScoreLine {
  abbr: string;
  /** 이닝별 득점. 아직 진행되지 않은 이닝은 null */
  innings: (number | null)[];
  runs: number;
  hits: number;
  errors: number;
}

interface BoxScoreTableProps {
  awayLine: BoxScoreLine;
  homeLine: BoxScoreLine;
}

// 이닝별 점수표 (진행·종료 공용, 공개 모드 전용 데이터).
function BoxScoreTable({ awayLine, homeLine }: BoxScoreTableProps) {
  const inningCount = Math.max(awayLine.innings.length, 9);
  const headers = Array.from({ length: inningCount }, (_, i) => `${i + 1}`);

  const renderLine = (line: BoxScoreLine, isHome: boolean) => (
    <tr className={isHome ? 'bg-red-tint-soft' : ''}>
      <th
        scope="row"
        className="px-3 py-2 text-left font-display text-[13px] font-semibold text-text-strong"
      >
        {line.abbr}
      </th>
      {headers.map((_, i) => (
        <td
          key={i}
          className="px-2 py-2 text-center font-display text-[13px] tabular-nums text-text-body"
        >
          {line.innings[i] ?? '-'}
        </td>
      ))}
      {[line.runs, line.hits, line.errors].map((value, i) => (
        <td
          key={`rhe-${i}`}
          className="px-2 py-2 text-center font-display text-[13px] font-bold tabular-nums text-text-strong"
        >
          {value}
        </td>
      ))}
    </tr>
  );

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[480px] border-collapse">
        <thead>
          <tr className="border-b border-divider">
            <th className="px-3 py-2" />
            {headers.map((h) => (
              <th
                key={h}
                className="px-2 py-2 text-center text-xs font-medium text-text-faint"
              >
                {h}
              </th>
            ))}
            {['R', 'H', 'E'].map((h) => (
              <th
                key={h}
                className="px-2 py-2 text-center text-xs font-bold text-text-muted"
              >
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {renderLine(awayLine, false)}
          {renderLine(homeLine, true)}
        </tbody>
      </table>
    </div>
  );
}

export default BoxScoreTable;
