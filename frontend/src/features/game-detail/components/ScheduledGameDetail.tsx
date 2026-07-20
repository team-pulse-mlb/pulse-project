import { Link } from 'react-router';

import Card from '../../../shared/components/Card';
import type {
    ScheduledGameDetailViewModel,
    StartingLineupPlayerViewModel,
} from '../model/gameDetailViewModels';
import RecommendedSidebar from './RecommendedSidebar';
import ScheduledMatchupHero from './ScheduledMatchupHero';

interface ScheduledGameDetailProps {
    data: ScheduledGameDetailViewModel;
    favoritePlayerNames: string[];
}

function normalizePlayerName(
    name: string,
): string {
    return name.trim().toLowerCase();
}

function isFavoritePlayer(
    playerName: string,
    favoritePlayerNames: string[],
): boolean {
    const normalizedPlayerName =
        normalizePlayerName(playerName);

    return favoritePlayerNames.some(
        (favoritePlayerName) =>
            normalizePlayerName(favoritePlayerName)
            === normalizedPlayerName,
    );
}

function FavoritePlayerMark() {
    return (
        <span
            aria-label="관심 선수"
            title="관심 선수"
            className="ml-1 inline-flex align-middle text-gold"
        >
            ★
        </span>
    );
}

interface StartingLineupCardProps {
    teamAbbr: string;
    players: StartingLineupPlayerViewModel[];
    favoritePlayerNames: string[];
}

function StartingLineupCard({
                                teamAbbr,
                                players,
                                favoritePlayerNames,
                            }: StartingLineupCardProps) {
    return (
        <div className="rounded-2xl border border-card-border bg-[#F8FAFC] p-4">
            <h4 className="border-b border-card-border pb-3 text-center font-display text-base font-bold text-mlb-navy">
                {teamAbbr}
            </h4>

            <div className="divide-y divide-divider">
                {players.map((player) => (
                    <div
                        key={`${teamAbbr}-${player.battingOrder}-${player.playerName}`}
                        className="grid grid-cols-[28px_minmax(0,1fr)_42px] items-center gap-2 py-3 text-sm"
                    >
                        <span className="font-display font-bold text-mlb-navy">
                            {player.battingOrder}
                        </span>

                        <span className="truncate font-semibold text-text-body">
                            {player.playerName}

                            {isFavoritePlayer(
                                player.playerName,
                                favoritePlayerNames,
                            ) ? (
                                <FavoritePlayerMark />
                            ) : null}
                        </span>

                        <span className="text-right text-xs font-semibold text-text-muted">
                            {player.position ?? '-'}
                        </span>
                    </div>
                ))}
            </div>
        </div>
    );
}

function ScheduledGameDetail({
                                 data,
                                 favoritePlayerNames,
                             }: ScheduledGameDetailProps) {
    /*
     * 선발 투수 정보가 없으면
     * 해당 팀 카드에 "미확정"을 표시한다.
     */
    const awayPitcher =
        data.probablePitchers.away
        ?? '미확정';

    const homePitcher =
        data.probablePitchers.home
        ?? '미확정';

    /*
     * 한 팀이라도 라인업이 없으면
     * 선발 라인업 영역 전체를 표시하지 않는다.
     */
    const hasCompleteStartingLineups =
        data.startingLineups.away.length > 0
        && data.startingLineups.home.length > 0;

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
                    <h3 className="text-[15px] font-bold text-text-strong">
                        선발 투수
                    </h3>

                    <div className="mt-5 grid grid-cols-2 divide-x divide-card-border">
                        <div className="px-3 text-center">
                            <p className="font-display text-sm font-bold text-mlb-navy">
                                {data.awayTeam.abbr}
                            </p>

                            <p className="mt-3 text-base font-bold text-text-strong">
                                {awayPitcher}

                                {isFavoritePlayer(
                                    awayPitcher,
                                    favoritePlayerNames,
                                ) ? (
                                    <FavoritePlayerMark />
                                ) : null}
                            </p>
                        </div>

                        <div className="px-3 text-center">
                            <p className="font-display text-sm font-bold text-mlb-navy">
                                {data.homeTeam.abbr}
                            </p>

                            <p className="mt-3 text-base font-bold text-text-strong">
                                {homePitcher}

                                {isFavoritePlayer(
                                    homePitcher,
                                    favoritePlayerNames,
                                ) ? (
                                    <FavoritePlayerMark />
                                ) : null}
                            </p>
                        </div>
                    </div>
                </Card>

                {hasCompleteStartingLineups && (
                    <Card>
                        <h3 className="text-[15px] font-bold text-text-strong">
                            선발 라인업
                        </h3>

                        <div className="mt-5 grid grid-cols-1 items-start gap-4 sm:grid-cols-2">
                            <StartingLineupCard
                                teamAbbr={data.awayTeam.abbr}
                                players={data.startingLineups.away}
                                favoritePlayerNames={
                                    favoritePlayerNames
                                }
                            />

                            <StartingLineupCard
                                teamAbbr={data.homeTeam.abbr}
                                players={data.startingLineups.home}
                                favoritePlayerNames={
                                    favoritePlayerNames
                                }
                            />
                        </div>
                    </Card>
                )}

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