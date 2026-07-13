import type { DisplayMode } from '../lib/displayMode';
import type { FixtureTeam } from '../fixtures/liveGameDetailFixture';

interface GameMatchupHeroProps {
    mode: DisplayMode;
    season: number;
    venue: string;
    inning: number;
    inningType: 'TOP' | 'BOTTOM';
    awayTeam: FixtureTeam;
    homeTeam: FixtureTeam;
    awayScore: number;
    homeScore: number;
}

interface TeamIdentityProps {
    team: FixtureTeam;
    align: 'left' | 'right';
}

function TeamIdentity({ team, align }: TeamIdentityProps) {
    const isAwaySide = align === 'right';

    const textBlock = (
        <div className={isAwaySide ? 'text-right' : 'text-left'}>
            <div className="font-display text-[25px] font-semibold leading-none text-white sm:text-[28px]">
                {team.abbr}
            </div>
            <div className="mt-1.5 text-xs font-medium text-white/45 sm:text-[13px]">
                {team.name}
            </div>
        </div>
    );

    const logoPlaceholder = (
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full border border-white/20 bg-white/10 font-display text-sm font-bold text-white/90 sm:h-14 sm:w-14">
            {team.abbr}
        </div>
    );

    return (
        <div
            className={`flex items-center gap-3 ${
                isAwaySide ? 'justify-end' : 'justify-start'
            }`}
        >
            {isAwaySide ? (
                <>
                    {textBlock}
                    {logoPlaceholder}
                </>
            ) : (
                <>
                    {logoPlaceholder}
                    {textBlock}
                </>
            )}
        </div>
    );
}

function GameMatchupHero({
                             mode,
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

    /**
     * 보호 모드에서는 초·말이 현재 공격팀을 드러낼 수 있으므로
     * 이닝 숫자만 표시한다.
     */
    const inningLabel = isRevealed
        ? `${inning}회 ${inningType === 'TOP' ? '초' : '말'}`
        : `${inning}회`;

    return (
        <section className="relative overflow-hidden rounded-hero bg-[linear-gradient(158deg,#0B2559_0%,#04122E_100%)] shadow-hero">
            {/* 프로토타입의 은은한 배경 조명을 CSS 이미지 없이 재현한다. */}
            <div className="pointer-events-none absolute -right-12 -top-20 h-56 w-56 rounded-full bg-[radial-gradient(circle,rgba(228,0,43,0.28),transparent_65%)]" />

            <div className="relative px-5 py-6 sm:px-8 sm:py-7">
                <div className="mb-7 flex flex-wrap items-center justify-between gap-3">
          <span className="inline-flex h-7 items-center gap-2 rounded-full bg-mlb-red px-3.5 text-xs font-bold tracking-[0.06em] text-white">
            <span className="h-1.5 w-1.5 rounded-full bg-white" />
            LIVE · {inningLabel}
          </span>

                    <span className="font-display text-xs font-medium text-white/50 sm:text-[13px]">
            {season} · {venue}
          </span>
                </div>

                <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 sm:gap-5">
                    <TeamIdentity team={awayTeam} align="right" />

                    {isRevealed ? (
                        <div
                            className="flex min-w-[94px] items-center justify-center gap-2 font-display text-[34px] font-semibold leading-none text-[#FF5A76] sm:min-w-[150px] sm:text-5xl"
                            aria-label={`${awayTeam.abbr} ${awayScore} 대 ${homeTeam.abbr} ${homeScore}`}
                        >
                            <span>{awayScore}</span>
                            <span className="text-xl font-medium text-white/30">:</span>
                            <span>{homeScore}</span>
                        </div>
                    ) : (
                        /**
                         * 점수 공간을 흐리거나 투명하게 숨기지 않는다.
                         * 보호 응답에는 점수 자체가 없다는 정책을 화면 구조에도 반영한다.
                         */
                        <div className="min-w-[70px] text-center font-display text-xl font-semibold tracking-[0.18em] text-white/35 sm:min-w-[150px]">
                            VS
                        </div>
                    )}

                    <TeamIdentity team={homeTeam} align="left" />
                </div>
            </div>
        </section>
    );
}

export default GameMatchupHero;