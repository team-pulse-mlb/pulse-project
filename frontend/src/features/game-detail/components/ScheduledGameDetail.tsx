import { Link } from 'react-router';

import Card from '../../../shared/components/Card';
import InfoRow from '../../../shared/components/InfoRow';
import type { ScheduledGameDetailFixture } from '../fixtures/scheduledGameDetailFixture';
import RecommendedSidebar from './RecommendedSidebar';
import ScheduledMatchupHero from './ScheduledMatchupHero';

interface ScheduledGameDetailProps {
    fixture: ScheduledGameDetailFixture;
}

function ScheduledGameDetail({
                                 fixture,
                             }: ScheduledGameDetailProps) {
    const probablePitchers = [
        `${fixture.awayTeam.abbr} ${fixture.probablePitchers.away}`,
        `${fixture.homeTeam.abbr} ${fixture.probablePitchers.home}`,
    ].join(' · ');

    return (
        <div className="mx-auto grid max-w-[1160px] grid-cols-1 items-start gap-10 px-4 py-7 sm:px-8 lg:grid-cols-[minmax(0,1fr)_336px]">
            <div className="flex min-w-0 flex-col gap-[18px]">
                {/* 예정 경기에는 공개할 결과가 없으므로 모드 토글을 두지 않는다. */}
                <div className="flex min-h-10 items-center">
                    <Link
                        to="/"
                        className="inline-flex items-center gap-1.5 text-[15px] font-semibold text-text-muted transition-colors hover:text-mlb-navy"
                    >
                        <span aria-hidden="true">←</span>
                        뒤로
                    </Link>
                </div>

                <ScheduledMatchupHero fixture={fixture} />

                <Card>
                    <h3 className="mb-3 text-[15px] font-bold text-text-strong">
                        경기 정보
                    </h3>

                    <div>
                        <InfoRow
                            label="구장"
                            value={fixture.venueDetail}
                        />

                        <InfoRow
                            label="시작 시각"
                            value={fixture.startTimeLabel}
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
                        {fixture.favoritePlayerNotice}
                    </p>
                </Card>
            </div>

            <aside className="lg:sticky lg:top-[86px]">
                <RecommendedSidebar currentGameId={fixture.gameId} />
            </aside>
        </div>
    );
}

export default ScheduledGameDetail;