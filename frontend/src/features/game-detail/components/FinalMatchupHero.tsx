import HeroTeamIdentity from '../../../shared/components/HeroTeamIdentity';
import StatusBadge from '../../../shared/components/StatusBadge';
import type { FinalGameDetailFixture } from '../fixtures/finalGameDetailFixture';
import type { DisplayMode } from '../lib/displayMode';

interface FinalMatchupHeroProps {
    fixture: FinalGameDetailFixture;
    mode: DisplayMode;
}

/**
 * 종료 경기의 상단 매치업 영역이다.
 */
function FinalMatchupHero({
                              fixture,
                              mode,
                          }: FinalMatchupHeroProps) {
    const isRevealed = mode === 'REVEALED';

    const headline = isRevealed
        ? fixture.revealedHeadline
        : fixture.protectedHeadline;

    return (
        <section className="relative overflow-hidden rounded-hero bg-[linear-gradient(158deg,#0B2559_0%,#04122E_100%)] shadow-hero">

            <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(120deg,transparent_0%,rgba(255,255,255,0.025)_45%,transparent_46%)]" />

            <div className="relative px-5 py-6 sm:px-8 sm:py-7">
                {/* 경기 상태와 경기 기본 정보 */}
                <div className="mb-8 flex flex-wrap items-start justify-between gap-4">

                    <StatusBadge status="final" />

                    <span className="font-display text-xs font-medium text-white/55 sm:text-[13px]">
            {fixture.season} · {fixture.dateLabel} · {fixture.venue}
          </span>
                </div>

                {/* 팀 매치업과 최종 점수 */}
                <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-3 sm:gap-5">
                    <HeroTeamIdentity
                        team={fixture.awayTeam}
                        align="right"
                    />

                    <div className="flex min-w-[86px] items-center justify-center sm:min-w-[150px]">
                        {isRevealed ? (
                            <div
                                className="grid grid-cols-[auto_auto_auto] items-center justify-center gap-3 font-display leading-none sm:gap-4"
                                aria-label={`${fixture.awayTeam.name} ${fixture.awayScore} 대 ${fixture.homeTeam.name} ${fixture.homeScore}`}
                            >
                <span className="text-[34px] font-semibold text-[#FF5A76] sm:text-5xl">
                  {fixture.awayScore}
                </span>

                                <span className="text-xs font-bold tracking-[0.08em] text-white/50 sm:text-sm">
                  VS
                </span>

                                <span className="text-[34px] font-semibold text-[#FF5A76] sm:text-5xl">
                  {fixture.homeScore}
                </span>
                            </div>
                        ) : (
                            <span className="font-display text-xl font-bold tracking-[0.08em] text-white/40">
                VS
              </span>
                        )}
                    </div>

                    <HeroTeamIdentity
                        team={fixture.homeTeam}
                        align="left"
                    />
                </div>

                {/*
         * 종료 경기 AI 헤드라인이 없으면
         * 구분선과 빈 공간도 렌더링하지 않는다.
         */}
                {headline && (
                    <div className="mt-7 border-t border-white/10 pt-5">
                        <p className="mx-auto max-w-[620px] text-center text-sm font-medium leading-relaxed text-white/75 sm:text-[15px]">
                            {headline}
                        </p>
                    </div>
                )}
            </div>
        </section>
    );
}

export default FinalMatchupHero;