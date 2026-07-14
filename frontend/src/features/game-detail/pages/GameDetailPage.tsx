import {
    useEffect,
    useState,
} from 'react';
import {
    Link,
    useParams,
} from 'react-router';

import BoxScoreTable from '../../../shared/components/BoxScoreTable';
import Card from '../../../shared/components/Card';
import EmptyState from '../../../shared/components/EmptyState';
import { useGameDetailQuery } from '../api/useGameDetailQuery';
import CurrentSituationCard from '../components/CurrentSituationCard';
import FinalGameDetail from '../components/FinalGameDetail';
import FinalMatchupHero from '../components/FinalMatchupHero';
import GameMatchupHero from '../components/GameMatchupHero';
import ModeToggle from '../components/ModeToggle';
import RecommendedSidebar from '../components/RecommendedSidebar';
import ScheduledGameDetail from '../components/ScheduledGameDetail';
import {
    getStoredMode,
    storeMode,
    type DisplayMode,
} from '../lib/displayMode';
import { toGameDetailViewModel } from '../lib/gameDetailMapper';

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
     * 사용자가 경기별로 마지막에 선택한 보호·공개 모드를 복원한다.
     *
     * 예정 경기는 서버가 항상 PROTECTED로 응답하며,
     * 화면에도 모드 토글을 표시하지 않는다.
     */
    const [mode, setMode] =
        useState<DisplayMode>(() =>
            gameId
                ? getStoredMode(gameId)
                : 'PROTECTED',
        );

    /**
     * URL의 gameId가 바뀌면 새 경기의 저장 모드를 다시 읽는다.
     */
    useEffect(() => {
        if (
            !gameId
            || validGameId === null
        ) {
            return;
        }

        setMode(
            getStoredMode(gameId),
        );
    }, [
        gameId,
        validGameId,
    ]);

    /**
     * 보호·공개 모드는 서로 다른 필드를 반환하므로
     * mode가 바뀌면 React Query가 새 API 요청을 실행한다.
     */
    const gameDetailQuery =
        useGameDetailQuery(
            validGameId,
            mode,
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

        setMode(nextMode);
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

    /**
     * 종료 경기 상세 화면이다.
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
     * 상세 응답에는 이벤트 타임라인과 최근 플레이가 없으므로
     * fixture 데이터를 섞지 않는다.
     * 이벤트는 별도의 /events API 연결 단계에서 추가한다.
     */
    const isRevealed =
        detail.displayMode
        === 'REVEALED';

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