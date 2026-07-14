import HeroTeamIdentity from '../../../shared/components/HeroTeamIdentity';
import StatusBadge from '../../../shared/components/StatusBadge';
import type { FinalGameDetailViewModel } from '../model/gameDetailViewModels';

interface FinalMatchupHeroProps {
    data: FinalGameDetailViewModel;
}

/**
 * 종료 경기 상단 매치업 영역이다.
 *
 * 보호 응답에서는 점수를 DOM에 생성하지 않고,
 * 공개 응답에서만 최종 점수를 표시한다.
 */
function FinalMatchupHero({
                              data,
                          }: FinalMatchupHeroProps) {
    const isRevealed =
        data.displayMode === 'REVEALED';

    const metaItems = [
        data.season?.toString() ?? null,
        data.dateLabel,
        data.venue,
    ].filter(
        (value): value is string =>
            value !== null
            && value.trim().length > 0,
    );

    const metaLabel = metaItems.join(' · ');

    const awayScoreLabel =
        data.awayScore ?? '-';

    const homeScoreLabel =
        data.homeScore ?? '-';

    return (
        <section className="relative overflow-hidden rounded-hero bg-[linear-gradient(158deg,#0B2559_0%,#04122E_100%)] shadow-hero">
            <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(120deg,transparent_0%,rgba(255,255,255,0.025)_45%,transparent_46%)]" />

            <div className="relative px-5 py-6 sm:px-8 sm:py-7">
                <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
                    <StatusBadge status="final" />

                    {metaLabel && (
                        <span className="font-display text-xs font-medium text-white/55 sm:text-[13px]">
                            {metaLabel}
                        </span>
                    )}
                </div>

                <div className="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-3 sm:gap-5">
                    <HeroTeamIdentity
                        team={data.awayTeam}
                        align="right"
                    />

                    <div className="flex min-w-[86px] items-center justify-center sm:min-w-[150px]">
                        {isRevealed ? (
                            <div
                                className="grid grid-cols-[auto_auto_auto] items-center justify-center gap-3 font-display leading-none sm:gap-4"
                                aria-label={`${data.awayTeam.name} ${awayScoreLabel} 대 ${data.homeTeam.name} ${homeScoreLabel}`}
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
                        team={data.homeTeam}
                        align="left"
                    />
                </div>

                {data.headline && (
                    <div className="mt-7 border-t border-white/10 pt-5">
                        <p className="mx-auto max-w-[620px] text-center text-sm font-medium leading-relaxed text-white/75 sm:text-[15px]">
                            {data.headline}
                        </p>
                    </div>
                )}
            </div>
        </section>
    );
}

export default FinalMatchupHero;