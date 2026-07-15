import { Link } from 'react-router';

import Card from '../../../shared/components/Card';
import InfoRow from '../../../shared/components/InfoRow';
import type { ScheduledGameDetailViewModel } from '../model/gameDetailViewModels';
import RecommendedSidebar from './RecommendedSidebar';
import ScheduledMatchupHero from './ScheduledMatchupHero';

interface ScheduledGameDetailProps {
    data: ScheduledGameDetailViewModel;
}

function ScheduledGameDetail({
                                 data,
                             }: ScheduledGameDetailProps) {
    /*
     * 예상 선발이 아직 확정되지 않았으면
     * API의 null 값을 화면에서 "미확정"으로 표시한다.
     */
    const awayPitcher =
        data.probablePitchers.away
        ?? '미확정';

    const homePitcher =
        data.probablePitchers.home
        ?? '미확정';

    const probablePitchers = [
        `${data.awayTeam.abbr} ${awayPitcher}`,
        `${data.homeTeam.abbr} ${homePitcher}`,
    ].join(' · ');

    const venueLabel =
        data.venue
        ?? '구장 미정';

    const startTimeLabel =
        data.startTimeLabel
        ?? '시작 시각 미정';

    return (
        <div className="mx-auto grid max-w-[1160px] grid-cols-1 items-start gap-10 px-4 py-7 sm:px-8 lg:grid-cols-[minmax(0,1fr)_336px]">
            <div className="flex min-w-0 flex-col gap-[18px]">
                {/*
                 * 예정 경기에는 공개할 결과가 없으므로
                 * 보호·공개 모드 토글을 표시하지 않는다.
                 */}
                <div className="flex min-h-10 items-center">
                    <Link
                        to="/"
                        className="inline-flex items-center gap-1.5 text-[15px] font-semibold text-text-muted transition-colors hover:text-mlb-navy"
                    >
                        <span aria-hidden="true">
                            ←
                        </span>
                        뒤로
                    </Link>
                </div>

                <ScheduledMatchupHero data={data} />

                <Card>
                    <h3 className="mb-3 text-[15px] font-bold text-text-strong">
                        경기 정보
                    </h3>

                    <div>
                        <InfoRow
                            label="구장"
                            value={venueLabel}
                        />

                        <InfoRow
                            label="시작 시각"
                            value={startTimeLabel}
                        />

                        <InfoRow
                            label="선발"
                            value={probablePitchers}
                        />
                    </div>
                </Card>

                <Card>
                    <h3 className="text-[15px] font-bold text-text-strong">
                        관심 선수 출전
                    </h3>

                    <p className="mt-2 text-sm leading-relaxed text-text-muted">
                        관심 선수를 등록하면 선발 라인업이 확정될 때
                        출전 여부를 알려드려요.
                    </p>
                </Card>
            </div>

            <aside className="lg:sticky lg:top-[86px]">
                <RecommendedSidebar
                    currentGameId={data.gameId}
                />
            </aside>
        </div>
    );
}

export default ScheduledGameDetail;