import { useState } from 'react';
import { Link, useParams } from 'react-router';

import EmptyState from '../../../shared/components/EmptyState';
import CurrentSituationCard from '../components/CurrentSituationCard';
import EventTimeline from '../components/EventTimeline';
import FinalGameDetail from '../components/FinalGameDetail';
import GameMatchupHero from '../components/GameMatchupHero';
import ModeToggle from '../components/ModeToggle';
import RecommendedSidebar from '../components/RecommendedSidebar';
import ScheduledGameDetail from '../components/ScheduledGameDetail';
import { finalGameDetailFixture } from '../fixtures/finalGameDetailFixture';
import { liveGameDetailFixture } from '../fixtures/liveGameDetailFixture';
import { scheduledGameDetailFixture } from '../fixtures/scheduledGameDetailFixture';
import {
    getStoredMode,
    storeMode,
    type DisplayMode,
} from '../lib/displayMode';

function GameDetailPage() {
    const { gameId } = useParams<{ gameId: string }>();

    /**
     * 훅은 경기 상태에 관계없이 항상 같은 순서로 호출한다.
     * 예정 경기에서는 상태를 사용하지 않고 토글도 렌더링하지 않는다.
     */
    const [mode, setMode] = useState<DisplayMode>(() =>
        gameId ? getStoredMode(gameId) : 'PROTECTED',
    );

    const parsedGameId = Number(gameId);

    if (
        !gameId ||
        !Number.isInteger(parsedGameId) ||
        parsedGameId <= 0
    ) {
        return (
            <div className="mx-auto max-w-[1160px] px-4 py-8">
                <EmptyState message="경기를 찾을 수 없습니다." />
            </div>
        );
    }

    const handleModeChange = (nextMode: DisplayMode) => {
        setMode(nextMode);
        storeMode(gameId, nextMode);
    };

    if (parsedGameId === scheduledGameDetailFixture.gameId) {
        return (
            <ScheduledGameDetail
                fixture={scheduledGameDetailFixture}
            />
        );
    }

    if (parsedGameId === finalGameDetailFixture.gameId) {
        return (
            <FinalGameDetail
                fixture={finalGameDetailFixture}
                mode={mode}
                onModeChange={handleModeChange}
            />
        );
    }

    return (
        <div className="mx-auto grid max-w-[1160px] grid-cols-1 items-start gap-10 px-4 py-7 sm:px-8 lg:grid-cols-[minmax(0,1fr)_336px]">
            <div className="flex min-w-0 flex-col gap-[18px]">
                <div className="flex items-center justify-between gap-4">
                    <Link
                        to="/"
                        className="inline-flex items-center gap-1.5 text-[15px] font-semibold text-text-muted transition-colors hover:text-mlb-navy"
                    >
                        <span aria-hidden="true">←</span>
                        뒤로
                    </Link>

                    <ModeToggle
                        mode={mode}
                        onChange={handleModeChange}
                    />
                </div>

                <GameMatchupHero
                    mode={mode}
                    season={liveGameDetailFixture.season}
                    venue={liveGameDetailFixture.venue}
                    inning={liveGameDetailFixture.inning}
                    inningType={liveGameDetailFixture.inningType}
                    awayTeam={liveGameDetailFixture.awayTeam}
                    homeTeam={liveGameDetailFixture.homeTeam}
                    awayScore={liveGameDetailFixture.awayScore}
                    homeScore={liveGameDetailFixture.homeScore}
                />

                <CurrentSituationCard
                    situation={liveGameDetailFixture.situation}
                />

                <EventTimeline
                    mode={mode}
                    events={liveGameDetailFixture.events}
                />
            </div>

            <aside className="lg:sticky lg:top-[86px]">
                <RecommendedSidebar currentGameId={parsedGameId} />
            </aside>
        </div>
    );
}

export default GameDetailPage;