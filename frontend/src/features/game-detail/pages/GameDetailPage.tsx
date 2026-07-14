import { useState } from 'react';
import {
    Link,
    useParams,
} from 'react-router';

import BoxScoreTable from '../../../shared/components/BoxScoreTable';
import Card from '../../../shared/components/Card';
import EmptyState from '../../../shared/components/EmptyState';
import { useGameDetailQuery } from '../api/useGameDetailQuery';
import { useGameEventsQuery } from '../api/useGameEventsQuery';
import { useGameRecentPlaysQuery } from '../api/useGameRecentPlaysQuery';
import CurrentSituationCard from '../components/CurrentSituationCard';
import EventTimeline from '../components/EventTimeline';
import FinalGameDetail from '../components/FinalGameDetail';
import FinalMatchupHero from '../components/FinalMatchupHero';
import GameMatchupHero from '../components/GameMatchupHero';
import ModeToggle from '../components/ModeToggle';
import RecentPlayList from '../components/RecentPlayList';
import RecommendedSidebar from '../components/RecommendedSidebar';
import ScheduledGameDetail from '../components/ScheduledGameDetail';
import {
    getStoredMode,
    storeMode,
    type DisplayMode,
} from '../lib/displayMode';
import { toGameDetailViewModel } from '../lib/gameDetailMapper';
import { toTimelineEvents } from '../lib/gameEventMapper';
import { toRecentPlayViewModels } from '../lib/gameRecentPlayMapper';

function GameDetailPage() {
    const { gameId } =
        useParams<{ gameId: string }>();

    const parsedGameId =
        Number(gameId);

    const validGameId =
        gameId
        && Number.isInteger(parsedGameId)
        && parsedGameId > 0
            ? parsedGameId
            : null;

    /**
     * 같은 GameDetailPage 안에서 URL의 gameId가 바뀌어도
     * 경기별 선택 모드를 독립적으로 유지한다.
     *
     * useEffect 안에서 setState를 호출하지 않아
     * 불필요한 연쇄 렌더링을 방지한다.
     */
    const [selectedModes, setSelectedModes] =
        useState<Record<string, DisplayMode>>({});

    const mode =
        gameId
            ? selectedModes[gameId]
            ?? getStoredMode(gameId)
            : 'PROTECTED';

    /**
     * 보호·공개 모드는 서로 다른 필드를 반환하므로
     * mode가 바뀌면 React Query가 새 API 요청을 실행한다.
     */
    const gameDetailQuery =
        useGameDetailQuery(
            validGameId,
            mode,
        );

    /**
     * 이벤트 타임라인은 진행 경기와 종료 경기에서만 조회한다.
     */
    const shouldFetchEvents =
        gameDetailQuery.data?.status
        === 'STATUS_IN_PROGRESS'
        || gameDetailQuery.data?.status
        === 'STATUS_FINAL';

    const gameEventsQuery =
        useGameEventsQuery(
            validGameId,
            mode,
            shouldFetchEvents,
        );

    /**
     * 최근 플레이에는 점수, 초·말, 실제 플레이 문구가 포함되므로
     * 진행·종료 경기의 공개 모드에서만 조회한다.
     */
    const shouldFetchRecentPlays =
        mode === 'REVEALED'
        && (
            gameDetailQuery.data?.status
            === 'STATUS_IN_PROGRESS'
            || gameDetailQuery.data?.status
            === 'STATUS_FINAL'
        );

    const recentPlaysQuery =
        useGameRecentPlaysQuery(
            validGameId,
            mode,
            shouldFetchRecentPlays,
        );

    if (validGameId === null) {
        return (
            <div className="mx-auto max-w-[1160px] px-4 py-8">
                <EmptyState message="경기를 찾을 수 없습니다." />
            </div>
        );
    }

    const handleModeChange = (
        nextMode: DisplayMode,
    ) => {
        if (!gameId) {
            return;
        }

        setSelectedModes(
            (previousModes) => ({
                ...previousModes,
                [gameId]: nextMode,
            }),
        );

        storeMode(
            gameId,
            nextMode,
        );
    };

    if (gameDetailQuery.isPending) {
        return (
            <div className="mx-auto max-w-[1160px] px-4 py-8 sm:px-8">
                <Card>
                    <div
                        className="py-10 text-center text-sm text-text-muted"
                        role="status"
                    >
                        경기 정보를 불러오는 중입니다.
                    </div>
                </Card>
            </div>
        );
    }

    if (
        gameDetailQuery.isError
        || !gameDetailQuery.data
    ) {
        return (
            <div className="mx-auto max-w-[1160px] px-4 py-8">
                <EmptyState message="경기 정보를 불러오지 못했습니다." />
            </div>
        );
    }

    const detail =
        toGameDetailViewModel(
            gameDetailQuery.data,
        );

    /**
     * 예정 경기는 결과 공개 개념이 없으므로
     * 별도 페이지를 사용하고 모드 토글을 표시하지 않는다.
     */
    if (detail.kind === 'SCHEDULED') {
        return (
            <ScheduledGameDetail
                data={detail}
            />
        );
    }

    const isRevealed =
        detail.displayMode === 'REVEALED';

    /**
     * 이벤트 API 응답을 기존 EventTimeline 화면 모델로 변환한다.
     */
    const timelineEvents =
        gameEventsQuery.data
            ? toTimelineEvents(
                gameEventsQuery.data,
            )
            : [];

    const eventTimelineContent =
        gameEventsQuery.isPending ? (
            <Card>
                <div
                    className="py-6 text-center text-sm text-text-muted"
                    role="status"
                >
                    이벤트를 불러오는 중입니다.
                </div>
            </Card>
        ) : (
            gameEventsQuery.isError
            || !gameEventsQuery.data
        ) ? (
            <Card>
                <p className="py-2 text-sm text-text-muted">
                    이벤트 타임라인을 불러오지 못했습니다.
                </p>
            </Card>
        ) : (
            <EventTimeline
                mode={detail.displayMode}
                events={timelineEvents}
            />
        );

    /**
     * 최근 플레이 API 응답을 화면 모델로 변환한다.
     *
     * 점수 표시는 상세 응답의 원정팀·홈팀 약어를 사용한다.
     */
    const recentPlays =
        recentPlaysQuery.data
            ? toRecentPlayViewModels(
                recentPlaysQuery.data,
                detail.awayTeam.abbr,
                detail.homeTeam.abbr,
            )
            : [];

    const recentPlayContent =
        recentPlaysQuery.isPending ? (
            <Card>
                <div
                    className="py-6 text-center text-sm text-text-muted"
                    role="status"
                >
                    최근 플레이를 불러오는 중입니다.
                </div>
            </Card>
        ) : (
            recentPlaysQuery.isError
            || !recentPlaysQuery.data
        ) ? (
            <Card>
                <p className="py-2 text-sm text-text-muted">
                    최근 플레이를 불러오지 못했습니다.
                </p>
            </Card>
        ) : (
            <RecentPlayList
                plays={recentPlays}
            />
        );

    /**
     * 종료 경기 상세 화면이다.
     *
     * 공개 모드 화면 순서:
     * 이닝별 점수판 → 이벤트 타임라인 → 최근 플레이
     */
    if (detail.kind === 'FINAL') {
        return (
            <div className="mx-auto grid max-w-[1160px] grid-cols-1 items-start gap-10 px-4 py-7 sm:px-8 lg:grid-cols-[minmax(0,1fr)_336px]">
                <div className="flex min-w-0 flex-col gap-[18px]">
                    <div className="flex items-center justify-between gap-4">
                        <Link
                            to="/"
                            className="inline-flex items-center gap-1.5 text-[15px] font-semibold text-text-muted transition-colors hover:text-mlb-navy"
                        >
                            <span aria-hidden="true">
                                ←
                            </span>
                            뒤로
                        </Link>

                        <ModeToggle
                            mode={
                                detail.displayMode
                            }
                            onChange={
                                handleModeChange
                            }
                        />
                    </div>

                    <FinalMatchupHero
                        data={detail}
                    />

                    <FinalGameDetail
                        data={detail}
                    />

                    {eventTimelineContent}

                    {isRevealed
                        && recentPlayContent}
                </div>

                <aside className="lg:sticky lg:top-[86px]">
                    <RecommendedSidebar
                        currentGameId={
                            detail.gameId
                        }
                    />
                </aside>
            </div>
        );
    }

    /**
     * 진행 경기 상세 화면이다.
     *
     * 이벤트 타임라인 아래에 최근 플레이를 배치하며,
     * 최근 플레이는 공개 모드에서만 렌더링한다.
     */
    return (
        <div className="mx-auto grid max-w-[1160px] grid-cols-1 items-start gap-10 px-4 py-7 sm:px-8 lg:grid-cols-[minmax(0,1fr)_336px]">
            <div className="flex min-w-0 flex-col gap-[18px]">
                <div className="flex items-center justify-between gap-4">
                    <Link
                        to="/"
                        className="inline-flex items-center gap-1.5 text-[15px] font-semibold text-text-muted transition-colors hover:text-mlb-navy"
                    >
                        <span aria-hidden="true">
                            ←
                        </span>
                        뒤로
                    </Link>

                    <ModeToggle
                        mode={
                            detail.displayMode
                        }
                        onChange={
                            handleModeChange
                        }
                    />
                </div>

                <GameMatchupHero
                    mode={
                        detail.displayMode
                    }
                    dateLabel={
                        detail.dateLabel
                    }
                    season={
                        detail.season
                    }
                    venue={
                        detail.venue
                    }
                    inning={
                        detail.inning
                    }
                    inningType={
                        detail.inningType
                    }
                    awayTeam={
                        detail.awayTeam
                    }
                    homeTeam={
                        detail.homeTeam
                    }
                    awayScore={
                        detail.awayScore
                    }
                    homeScore={
                        detail.homeScore
                    }
                />

                {isRevealed
                    && detail.inningScores && (
                        <Card flush>
                            <div className="px-3 py-2 sm:px-4 sm:py-2.5">
                                <BoxScoreTable
                                    awayLine={
                                        detail
                                            .inningScores
                                            .awayLine
                                    }
                                    homeLine={
                                        detail
                                            .inningScores
                                            .homeLine
                                    }
                                />
                            </div>
                        </Card>
                    )}

                <CurrentSituationCard
                    mode={
                        detail.displayMode
                    }
                    situation={
                        detail.situation
                    }
                    matchup={
                        isRevealed
                            ? detail.currentMatchup
                            : null
                    }
                />

                {eventTimelineContent}

                {isRevealed
                    && recentPlayContent}
            </div>

            <aside className="lg:sticky lg:top-[86px]">
                <RecommendedSidebar
                    currentGameId={
                        detail.gameId
                    }
                />
            </aside>
        </div>
    );
}

export default GameDetailPage;