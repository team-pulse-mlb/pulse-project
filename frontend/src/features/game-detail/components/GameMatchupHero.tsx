import HeroTeamIdentity from '../../../shared/components/HeroTeamIdentity';
import StatusBadge from '../../../shared/components/StatusBadge';
import type { DisplayMode } from '../lib/displayMode';
import type {
    GameDetailTeamViewModel,
    ViewInningType,
} from '../model/gameDetailViewModels';

interface GameMatchupHeroProps {
    mode: DisplayMode;

    dateLabel: string | null;
    season: number | null;
    venue: string | null;

    inning: number | null;
    inningType: ViewInningType | null;

    awayTeam: GameDetailTeamViewModel;
    homeTeam: GameDetailTeamViewModel;

    awayScore: number | null;
    homeScore: number | null;
}

/**
 * 진행 경기 상단 매치업 영역이다.
 *
 * 보호 모드에서는 초·말과 점수를 DOM에 생성하지 않는다.
 * API 데이터가 아직 없거나 nullable이면 임의 값을 만들지 않고
 * 사용 가능한 정보만 표시한다.
 */
function GameMatchupHero({
                             mode,
                             dateLabel,
                             season,
                             venue,
                             inning,
                             inningType,
                             awayTeam,
                             homeTeam,
                             awayScore,
                             homeScore,
                         }: GameMatchupHeroProps) {
    const isRevealed = mode === 'REVEALED';

    const inningLabel = (() => {
        if (inning === null) {
            return '진행 중';
        }

        if (
            isRevealed
            && inningType !== null
        ) {
            return `${inning}회 ${
                inningType === 'TOP'
                    ? '초'
                    : '말'
            }`;
        }

        return `${inning}회`;
    })();

    const metaItems = [
        season?.toString() ?? null,
        dateLabel,
        venue,
    ].filter(
        (value): value is string =>
            value !== null
            && value.trim().length > 0,
    );

    const metaLabel = metaItems.join(' · ');

    const awayScoreLabel =
        awayScore ?? '-';

    const homeScoreLabel =
        homeScore ?? '-';

    return (
        <section className="relative overflow-hidden rounded-hero bg-[linear-gradient(158deg,#0B2559_0%,#04122E_100%)] shadow-hero">
            <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(120deg,transparent_0%,rgba(255,255,255,0.025)_45%,transparent_46%)]" />

            <div className="relative px-5 py-6 sm:px-8 sm:py-7">
                <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
                    <div className="flex items-center gap-2">
                        <StatusBadge status="live" />

                        <span className="font-display text-sm font-bold text-white sm:text-base">
                            {inningLabel}
                        </span>
                    </div>

                    {metaLabel && (
                        <span className="font-display text-xs font-medium text-white/55 sm:text-[13px]">
                            {metaLabel}
                        </span>
                    )}
                </div>

                <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-3 sm:gap-5">
                    <HeroTeamIdentity
                        team={awayTeam}
                        align="right"
                    />

                    <div className="flex min-w-[86px] items-center justify-center sm:min-w-[150px]">
                        {isRevealed ? (
                            <div
                                className="grid grid-cols-[auto_auto_auto] items-center justify-center gap-3 font-display leading-none sm:gap-4"
                                aria-label={`${awayTeam.name} ${awayScoreLabel} 대 ${homeTeam.name} ${homeScoreLabel}`}
                            >
                                <span className="text-[34px] font-semibold text-[#FF5A76] sm:text-5xl">
                                    {awayScoreLabel}
                                </span>

                                <span className="text-xs font-bold tracking-[0.08em] text-white/50 sm:text-sm">
                                    VS
                                </span>

                                <span className="text-[34px] font-semibold text-[#FF5A76] sm:text-5xl">
                                    {homeScoreLabel}
                                </span>
                            </div>
                        ) : (
                            <span className="font-display text-xl font-bold tracking-[0.08em] text-white/40">
                                VS
                            </span>
                        )}
                    </div>

                    <HeroTeamIdentity
                        team={homeTeam}
                        align="left"
                    />
                </div>
            </div>
        </section>
    );
}

export default GameMatchupHero;