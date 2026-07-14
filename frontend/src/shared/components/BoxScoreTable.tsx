export interface BoxScoreLine {
    abbr: string;

    /**
     * 이닝별 득점이다.
     * 아직 진행되지 않았거나 공격하지 않은 이닝은 null이다.
     */
    innings: (number | null)[];

    runs: number;
    hits: number;
    errors: number;
}

interface BoxScoreTableProps {
    awayLine: BoxScoreLine;
    homeLine: BoxScoreLine;
}

const TOTAL_HEADERS = ['R', 'H', 'E'] as const;

/**
 * 진행·종료 경기에서 공통으로 사용하는 공개 모드 전용 점수표다.
 *
 * 기본적으로 9회까지 표시하고,
 * 10회 이상 데이터가 들어오면 연장 이닝 열을 자동으로 추가한다.
 */
function BoxScoreTable({
                           awayLine,
                           homeLine,
                       }: BoxScoreTableProps) {
    const inningCount = Math.max(
        9,
        awayLine.innings.length,
        homeLine.innings.length,
    );

    const inningHeaders = Array.from(
        { length: inningCount },
        (_, index) => index + 1,
    );

    const renderLine = (line: BoxScoreLine) => {
        const totals = [
            line.runs,
            line.hits,
            line.errors,
        ];

        return (
            <tr key={line.abbr} className="bg-white">
                {/*
         * 가로 스크롤 중에도 어느 팀 행인지 확인할 수 있도록
         * 팀 약어 열을 왼쪽에 고정한다.
         */}
                <th
                    scope="row"
                    className="sticky left-0 z-10 min-w-[62px] border-t border-divider bg-white px-3 py-2.5 text-left font-display text-sm font-bold text-text-strong"
                >
                    {line.abbr}
                </th>

                {inningHeaders.map((inningNumber, index) => (
                    <td
                        key={`${line.abbr}-inning-${inningNumber}`}
                        className="min-w-10 border-t border-divider px-2 py-2.5 text-center font-display text-[13px] font-medium text-text-body"
                    >
                        {line.innings[index] ?? '-'}
                    </td>
                ))}

                {totals.map((value, index) => (
                    <td
                        key={`${line.abbr}-total-${TOTAL_HEADERS[index]}`}
                        className="min-w-11 border-t border-divider px-2 py-2.5 text-center font-display text-sm font-extrabold text-text-strong"
                    >
                        {value}
                    </td>
                ))}
            </tr>
        );
    };

    return (
        <div
            className="w-full overflow-x-auto overscroll-x-contain"
            aria-label="이닝별 점수표"
        >
            <table className="w-full min-w-max border-collapse">
                <thead>
                <tr>
                    <th
                        scope="col"
                        className="sticky left-0 z-20 min-w-[62px] bg-white px-3 py-2 text-left text-xs font-bold text-text-muted"
                    >
                        팀
                    </th>

                    {inningHeaders.map((inningNumber) => (
                        <th
                            key={`header-${inningNumber}`}
                            scope="col"
                            className="min-w-10 px-2 py-2 text-center font-display text-xs font-bold text-text-muted"
                        >
                            {inningNumber}
                        </th>
                    ))}

                    {TOTAL_HEADERS.map((header) => (
                        <th
                            key={header}
                            scope="col"
                            className="min-w-11 px-2 py-2 text-center font-display text-xs font-extrabold text-mlb-navy"
                        >
                            {header}
                        </th>
                    ))}
                </tr>
                </thead>

                <tbody>
                {renderLine(awayLine)}
                {renderLine(homeLine)}
                </tbody>
            </table>
        </div>
    );
}

export default BoxScoreTable;