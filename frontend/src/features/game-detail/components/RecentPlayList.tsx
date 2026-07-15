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

interface RecentPlayGroup {
    key: string;
    label: string;
    plays: RecentPlayViewModel[];
}

function getHalfInningKey(
    play: RecentPlayViewModel,
) {
    return `${play.inning}-${play.inningType}`;
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
 * 같은 이닝 초·말에 속한 최근 플레이를 하나의 그룹으로 묶는다.
 *
 * Map은 첫 번째로 등장한 그룹 순서를 유지하므로
 * API가 반환한 최신 플레이 우선 순서도 그대로 유지된다.
 */
function groupPlaysByHalfInning(
    plays: RecentPlayViewModel[],
): RecentPlayGroup[] {
    const groups =
        new Map<string, RecentPlayGroup>();

    plays.forEach((play) => {
        const key =
            getHalfInningKey(play);

        const existingGroup =
            groups.get(key);

        if (existingGroup) {
            existingGroup.plays.push(play);
            return;
        }

        groups.set(key, {
            key,
            label: getHalfInningLabel(
                play.inning,
                play.inningType,
            ),
            plays: [play],
        });
    });

    return Array.from(
        groups.values(),
    );
}

/**
 * 공개 모드의 경기 흐름을 최근 플레이로 표시한다.
 *
 * 최근 플레이에는 초·말, 점수, 타석 결과가 포함되므로
 * GameDetailPage의 공개 모드 분기 안에서만 렌더링한다.
 *
 * API가 반환한 LLM 번역 플레이 문구는 화면에서 다시 요약하거나
 * 조합하지 않고 그대로 표시한다.
 */
function RecentPlayList({
                            title = '경기 흐름',
                            plays,
                        }: RecentPlayListProps) {
    const groups =
        groupPlaysByHalfInning(plays);

    return (
        <Card className="overflow-hidden">
            <h3 className="mb-5 text-[15px] font-bold text-text-strong">
                {title}
            </h3>

            {groups.length === 0 ? (
                <p className="text-sm text-text-muted">
                    아직 기록된 경기 흐름이 없습니다.
                </p>
            ) : (
                /*
                 * 카드 제목은 고정하고
                 * 경기 흐름 목록만 세로로 스크롤한다.
                 */
                <div className="max-h-[380px] overflow-y-auto overscroll-contain pr-2 sm:max-h-[430px] [scrollbar-gutter:stable]">
                    <div className="flex flex-col gap-4">
                        {groups.map((group) => (
                            <section
                                key={group.key}
                                className="rounded-xl border border-divider px-4 py-3.5"
                            >
                                {/*
                                 * 공개 모드의 이닝 초·말 표시는
                                 * 네이비 배경과 흰색 글자로 강조한다.
                                 */}
                                <div>
                                    <span className="inline-flex rounded-full bg-mlb-navy px-3.5 py-1.5 font-display text-[13px] font-bold text-white">
                                        {group.label}
                                    </span>
                                </div>

                                <ul className="mt-3 space-y-3">
                                    {group.plays.map((play) => (
                                        <li
                                            key={play.playId}
                                            className="grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3"
                                        >
                                            {/*
                                             * 점, 문장, 점수를 동일한 행에 배치한다.
                                             * 점은 프로젝트의 MLB 레드 색상을 사용한다.
                                             */}
                                            <span
                                                aria-hidden="true"
                                                className="h-2 w-2 shrink-0 rounded-full bg-mlb-red"
                                            />

                                            <p className="min-w-0 text-[15px] leading-relaxed text-text-body">
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

                                            <span className="whitespace-nowrap font-display text-sm font-semibold text-mlb-navy">
                                                {play.scoreLabel}
                                            </span>
                                        </li>
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

export default RecentPlayList;