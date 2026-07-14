import Card from '../../../shared/components/Card';
import type { TimelineEvent } from './EventTimeline';

interface ScoringPlayListProps {
    plays: TimelineEvent[];
}

function getInningLabel(play: TimelineEvent) {
    if (!play.inningType) {
        return `${play.inning}회`;
    }

    return `${play.inning}회 ${
        play.inningType === 'TOP' ? '초' : '말'
    }`;
}

/**
 * 종료 경기 공개 모드의 득점 플레이 목록이다.
 *
 * 최초 HTML 프로토타입의 play-list 구조를 따라
 * 흰 카드 안에서 각 플레이를 얇은 구분선으로 나눈다.
 *
 * 이벤트 타임라인과는 다음 요소로 구분한다.
 * - 왼쪽의 '득점' 유형 배지
 * - 별도의 이닝 열
 * - 타임라인 점과 연결선이 없는 가로형 목록
 */
function ScoringPlayList({
                             plays,
                         }: ScoringPlayListProps) {
    return (
        <Card className="overflow-hidden">
            <div className="mb-1 flex flex-wrap items-end justify-between gap-2">
                <div>
                    <h3 className="text-[15px] font-bold text-text-strong">
                        득점 플레이
                    </h3>

                    <p className="mt-1 text-xs text-text-faint">
                        경기에서 득점이 발생한 장면입니다.
                    </p>
                </div>

                <span className="text-xs font-semibold text-text-faint">
          총 {plays.length}개
        </span>
            </div>

            {plays.length === 0 ? (
                <p className="mt-5 text-sm text-text-muted">
                    기록된 득점 플레이가 없습니다.
                </p>
            ) : (
                /**
                 * 목록이 길어져도 카드 높이가 무한히 늘어나지 않도록
                 * 플레이 영역만 세로 스크롤한다.
                 */
                <div className="mt-4 max-h-[380px] overflow-y-auto overscroll-contain pr-2 [scrollbar-gutter:stable]">
                    <ul>
                        {plays.map((play) => (
                            <li
                                key={play.eventId}
                                className="grid grid-cols-[58px_64px_minmax(0,1fr)] items-center gap-3 border-b border-divider py-3.5 last:border-b-0 sm:grid-cols-[64px_76px_minmax(0,1fr)]"
                            >
                                {/* 프로토타입의 play-type score 스타일 */}
                                <span className="rounded-md bg-red-tint px-2 py-1.5 text-center text-[11px] font-extrabold text-mlb-red">
                  득점
                </span>

                                <span className="font-display text-xs font-semibold text-text-muted">
                  {getInningLabel(play)}
                </span>

                                <p className="min-w-0 text-sm leading-relaxed text-text-body">
                                    {play.highlighted && (
                                        <span
                                            aria-label="주요 득점 플레이"
                                            className="mr-1.5 text-gold"
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

export default ScoringPlayList;