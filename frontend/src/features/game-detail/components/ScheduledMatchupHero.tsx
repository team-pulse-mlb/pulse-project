import type {
    ScheduledFixtureTeam,
    ScheduledGameDetailFixture,
} from '../fixtures/scheduledGameDetailFixture';

interface ScheduledMatchupHeroProps {
    fixture: ScheduledGameDetailFixture;
}

interface TeamIdentityProps {
    team: ScheduledFixtureTeam;
    align: 'left' | 'right';
}

function TeamIdentity({ team, align }: TeamIdentityProps) {
    const isAwaySide = align === 'right';

    const logo = (
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full border border-white/20 bg-white/10 font-display text-sm font-bold text-white sm:h-14 sm:w-14">
            {team.abbr}
        </div>
    );

    const text = (
        <div className={isAwaySide ? 'text-right' : 'text-left'}>
            <div className="font-display text-[25px] font-semibold leading-none text-white sm:text-[28px]">
                {team.abbr}
            </div>

            <div className="mt-1.5 text-xs font-medium text-white/45 sm:text-[13px]">
                {team.name}
            </div>
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
                    {text}
                    {logo}
                </>
            ) : (
                <>
                    {logo}
                    {text}
                </>
            )}
        </div>
    );
}

function ScheduledMatchupHero({
                                  fixture,
                              }: ScheduledMatchupHeroProps) {
    return (
        <section className="relative overflow-hidden rounded-hero bg-[linear-gradient(158deg,#0B2559_0%,#04122E_100%)] shadow-hero">
            {/* 이미지 없이 프로토타입의 은은한 배경 조명을 표현한다. */}
            <div className="pointer-events-none absolute -right-12 -top-20 h-56 w-56 rounded-full bg-[radial-gradient(circle,rgba(255,255,255,0.12),transparent_65%)]" />

            <div className="relative px-5 py-6 sm:px-8 sm:py-7">
                <div className="mb-7 flex flex-wrap items-center justify-between gap-3">
          <span className="inline-flex h-7 items-center rounded-full border border-white/15 bg-white/10 px-3.5 text-xs font-bold tracking-[0.04em] text-white">
            {fixture.badgeLabel}
          </span>

                    <span className="font-display text-xs font-medium text-white/50 sm:text-[13px]">
            {fixture.season} · {fixture.venue}
          </span>
                </div>

                <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3 sm:gap-5">
                    <TeamIdentity
                        team={fixture.awayTeam}
                        align="right"
                    />

                    <div
                        className="min-w-[54px] text-center font-display text-xl font-semibold text-white/40 sm:min-w-[100px] sm:text-2xl"
                        aria-label={`${fixture.awayTeam.abbr} 대 ${fixture.homeTeam.abbr}`}
                    >
                        @
                    </div>

                    <TeamIdentity
                        team={fixture.homeTeam}
                        align="left"
                    />
                </div>
            </div>
        </section>
    );
}

export default ScheduledMatchupHero;