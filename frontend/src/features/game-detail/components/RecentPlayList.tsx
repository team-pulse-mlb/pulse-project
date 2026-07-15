import Card from '../../../shared/components/Card';

import type {
    RecentPlayViewModel,
} from '../model/gameRecentPlayViewModels';

interface RecentPlayListProps {
    /*
     * 보호 이벤트 목록과 공개 최근 플레이 목록 모두
     * 화면에서는 '경기 흐름'이라는 동일한 섹션명을 사용한다.
     */
    title?: string;
    plays: RecentPlayViewModel[];
}

function getHalfInningLabel(
    inning: number,
    inningType: 'TOP' | 'BOTTOM',
) {
    return `${inning}회 ${
        inningType === 'TOP' ? '초' : '말'
    }`;
}

/**
 * 공개 모드의 경기 흐름을 최근 플레이로 표시한다.
 *
 * 최근 플레이에는 초·말, 점수, 타석 결과가 포함되므로
 * GameDetailPage의 공개 모드 분기 안에서만 렌더링한다.
 *
 * API가 반환한 플레이 문구는 화면에서 다시 요약하거나
 * 조합하지 않고 그대로 표시한다.
 */
function RecentPlayList({
    title = '경기 흐름',
    plays,
}: RecentPlayListProps) {
    return (
        <Card className="overflow-hidden">
            <h3 className="mb-5 text-[15px] font-bold text-text-strong">
                {title}
            </h3>

            {plays.length === 0 ? (
                <p className="text-sm text-text-muted">
                    아직 기록된 경기 흐름이 없습니다.
                </p>
            ) : (
                /*
                 * 카드 전체가 길어지는 것을 막기 위해
                 * 경기 흐름 목록 부분만 세로 스크롤한다.
                 */
                <div className="max-h-[320px] overflow-y-auto overscroll-contain pr-2 sm:max-h-[380px] [scrollbar-gutter:stable]">
                    <ul>
                        {plays.map((play) => (
                            <li
                                key={play.playId}
                                className="border-t border-divider py-4 first:border-t-0 first:pt-0 last:pb-0"
                            >
                                <div className="flex flex-wrap items-center justify-between gap-3">
                                    <span className="font-display text-xs font-bold text-mlb-red">
                                        {getHalfInningLabel(
                                            play.inning,
                                            play.inningType,
                                        )}
                                    </span>

                                    <span className="font-display text-sm font-semibold text-mlb-navy">
                                        {play.scoreLabel}
                                    </span>
                                </div>

                                <p className="mt-2 text-sm leading-relaxed text-text-body">
                                    {play.highlighted && (
                                        <span
                                            aria-label="관심 선수"
                                            className="mr-1 text-gold"
                                        >
                                            ★
                                        </span>
                                    )}

                                    {play.text}
                                </p>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </Card>
    );
}

export default RecentPlayList;