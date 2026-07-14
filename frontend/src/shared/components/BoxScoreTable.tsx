export interface BoxScoreLine {
    abbr: string;

    /**
     * 이닝별 득점이다.
     * 아직 진행되지 않았거나 공격하지 않은 이닝은 null이다.
     */
    innings: Array<number | null>;

    /**
     * 경기의 총 득점이다.
     * 상세 API에 값이 없으면 '-'로 표시한다.
     */
    runs: number | null;
}

interface BoxScoreTableProps {
    awayLine: BoxScoreLine;
    homeLine: BoxScoreLine;
}

/**
 * 진행·종료 경기에서 공통으로 사용하는 공개 모드 전용 점수표다.
 *
 * 이닝별 득점과 총 득점(R)만 표시한다.
 * 현재 상세 API가 제공하지 않는 H·E 열은 만들지 않는다.
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

    const renderLine = (line: BoxScoreLine) => (
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

            {inningHeaders.map(
                (inningNumber, index) => (
                    <td
                        key={`${line.abbr}-inning-${inningNumber}`}
                        className="min-w-10 border-t border-divider px-2 py-2.5 text-center font-display text-[13px] font-medium text-text-body"
                    >
                        {line.innings[index] ?? '-'}
                    </td>
                ),
            )}

            <td className="min-w-11 border-t border-divider px-2 py-2.5 text-center font-display text-sm font-extrabold text-text-strong">
                {line.runs ?? '-'}
            </td>
        </tr>
    );

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

                    {inningHeaders.map(
                        (inningNumber) => (
                            <th
                                key={`header-${inningNumber}`}
                                scope="col"
                                className="min-w-10 px-2 py-2 text-center font-display text-xs font-bold text-text-muted"
                            >
                                {inningNumber}
                            </th>
                        ),
                    )}

                    <th
                        scope="col"
                        className="min-w-11 px-2 py-2 text-center font-display text-xs font-extrabold text-mlb-navy"
                    >
                        R
                    </th>
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