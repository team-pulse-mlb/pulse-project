import HeroTeamIdentity from '../../../shared/components/HeroTeamIdentity';
import StatusBadge from '../../../shared/components/StatusBadge';
import type { ScheduledGameDetailFixture } from '../fixtures/scheduledGameDetailFixture';

interface ScheduledMatchupHeroProps {
    fixture: ScheduledGameDetailFixture;
}

/**
 * 예정 경기 상단 매치업 영역.
 *
 * 왼쪽 상단에는 녹색 경기 예정 배지를 표시하고,
 * 오른쪽 상단에는 연도·날짜·구장 정보를 표시한다.
 */
function ScheduledMatchupHero({
                                  fixture,
                              }: ScheduledMatchupHeroProps) {
    return (
        <section className="relative overflow-hidden rounded-hero bg-[linear-gradient(158deg,#0B2559_0%,#04122E_100%)] shadow-hero">
            <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(120deg,transparent_0%,rgba(255,255,255,0.025)_45%,transparent_46%)]" />
            <div className="relative px-5 py-6 sm:px-8 sm:py-7">
                {/* 경기 상태와 기본 정보 */}
                <div className="mb-8 flex flex-wrap items-start justify-between gap-4">

                    <StatusBadge status="scheduled" />

                    <span className="font-display text-xs font-medium text-white/55 sm:text-[13px]">
            {fixture.season} · {fixture.dateLabel} · {fixture.venue}
          </span>
                </div>

                {/* 팀 매치업 */}
                <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-3 sm:gap-5">
                    <HeroTeamIdentity
                        team={fixture.awayTeam}
                        align="right"
                    />

                    <div
                        className="min-w-[70px] text-center font-display text-xl font-bold tracking-[0.08em] text-white/40 sm:min-w-[120px]"
                        aria-label={`${fixture.awayTeam.name} 대 ${fixture.homeTeam.name}`}
                    >
                        VS
                    </div>

                    <HeroTeamIdentity
                        team={fixture.homeTeam}
                        align="left"
                    />
                </div>
            </div>
        </section>
    );
}

export default ScheduledMatchupHero;