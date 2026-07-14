import { useState } from 'react';
import { Link, useParams } from 'react-router';

import BoxScoreTable from '../../../shared/components/BoxScoreTable';
import Card from '../../../shared/components/Card';
import EmptyState from '../../../shared/components/EmptyState';
import CurrentSituationCard from '../components/CurrentSituationCard';
import EventTimeline from '../components/EventTimeline';
import FinalGameDetail from '../components/FinalGameDetail';
import FinalMatchupHero from '../components/FinalMatchupHero';
import GameMatchupHero from '../components/GameMatchupHero';
import ModeToggle from '../components/ModeToggle';
import RecentPlayList from '../components/RecentPlayList';
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
     * 훅은 경기 상태와 관계없이 항상 같은 순서로 호출한다.
     * 예정 경기에는 토글이 없지만 훅 호출 구조는 유지한다.
     */
    const [mode, setMode] = useState<DisplayMode>(() =>
        gameId ? getStoredMode(gameId) : 'PROTECTED',
    );

    const parsedGameId = Number(gameId);
    const isRevealed = mode === 'REVEALED';

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

    /**
     * 예정 경기에는 보호·공개 모드가 없으므로
     * 예정 경기 전용 페이지 구조를 그대로 사용한다.
     */
    if (parsedGameId === scheduledGameDetailFixture.gameId) {
        return (
            <ScheduledGameDetail
                fixture={scheduledGameDetailFixture}
            />
        );
    }


    if (parsedGameId === finalGameDetailFixture.gameId) {
        return (
            <div className="mx-auto grid max-w-[1160px] grid-cols-1 items-start gap-10 px-4 py-7 sm:px-8 lg:grid-cols-[minmax(0,1fr)_336px]">
                <div className="flex min-w-0 flex-col gap-[18px]">
                    {/* 뒤로가기와 종료 경기 모드 토글 */}
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

                    {/* 종료 경기 매치업과 최종 점수 */}
                    <FinalMatchupHero
                        fixture={finalGameDetailFixture}
                        mode={mode}
                    />

                    {/*
           * 종료 경기 본문 출력 순서:
           * 이닝별 점수 → 경기 흐름 → 이벤트 타임라인 → 득점 플레이
           */}
                    <FinalGameDetail
                        fixture={finalGameDetailFixture}
                        mode={mode}
                    />
                </div>

                <aside className="lg:sticky lg:top-[86px]">
                    <RecommendedSidebar
                        currentGameId={parsedGameId}
                    />
                </aside>
            </div>
        );
    }

    /**
     * 진행 중 경기 상세 화면이다.
     */
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
                    dateLabel={liveGameDetailFixture.dateLabel}
                    season={liveGameDetailFixture.season}
                    venue={liveGameDetailFixture.venue}
                    inning={liveGameDetailFixture.inning}
                    inningType={liveGameDetailFixture.inningType}
                    awayTeam={liveGameDetailFixture.awayTeam}
                    homeTeam={liveGameDetailFixture.homeTeam}
                    awayScore={liveGameDetailFixture.awayScore}
                    homeScore={liveGameDetailFixture.homeScore}
                />

                {/**
                 * 진행 경기 공개 모드에서 이닝별 점수 데이터가 있을 때만
                 * 이닝별 점수 카드 전체를 표시한다.
                 */}
                {isRevealed &&
                    liveGameDetailFixture.inningScores && (
                        <Card flush>
                            <div className="px-3 py-2 sm:px-4 sm:py-2.5">
                                <BoxScoreTable
                                    awayLine={
                                        liveGameDetailFixture.inningScores.awayLine
                                    }
                                    homeLine={
                                        liveGameDetailFixture.inningScores.homeLine
                                    }
                                />
                            </div>
                        </Card>
                    )}

                <CurrentSituationCard
                    mode={mode}
                    situation={liveGameDetailFixture.situation}
                    matchup={
                        isRevealed
                            ? liveGameDetailFixture.currentMatchup
                            : null
                    }
                />

                <EventTimeline
                    mode={mode}
                    events={liveGameDetailFixture.events}
                />

                {/**
                 * 최근 플레이에는 점수와 타석 결과가 있으므로
                 * 공개 모드에서만 표시한다.
                 */}
                {isRevealed && (
                    <RecentPlayList
                        plays={liveGameDetailFixture.recentPlays}
                    />
                )}
            </div>

            <aside className="lg:sticky lg:top-[86px]">
                <RecommendedSidebar
                    currentGameId={parsedGameId}
                />
            </aside>
        </div>
    );
}

export default GameDetailPage;