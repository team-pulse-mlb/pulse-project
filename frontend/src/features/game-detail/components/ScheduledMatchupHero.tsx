import HeroTeamIdentity from '../../../shared/components/HeroTeamIdentity';
import StatusBadge from '../../../shared/components/StatusBadge';
import type { ScheduledGameDetailViewModel } from '../model/gameDetailViewModels';

interface ScheduledMatchupHeroProps {
    data: ScheduledGameDetailViewModel;
}

/**
 * 예정 경기 상단 매치업 영역이다.
 *
 * 예정 경기에는 공개할 결과가 없으므로 점수나 모드 토글 없이
 * 경기 상태, 날짜, 구장과 팀 매치업만 표시한다.
 */
function ScheduledMatchupHero({
                                  data,
                              }: ScheduledMatchupHeroProps) {
    /*
     * API에서 시작 시각이나 구장이 누락된 경우
     * 존재하는 정보만 조합해서 표시한다.
     */
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

    return (
        <section className="relative overflow-hidden rounded-hero bg-[linear-gradient(158deg,#0B2559_0%,#04122E_100%)] shadow-hero">
            <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(120deg,transparent_0%,rgba(255,255,255,0.025)_45%,transparent_46%)]" />

            <div className="relative px-5 py-6 sm:px-8 sm:py-7">
                <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
                    <StatusBadge status="scheduled" />

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

                    <div
                        className="min-w-[70px] text-center font-display text-xl font-bold tracking-[0.08em] text-white/40 sm:min-w-[120px]"
                        aria-label={`${data.awayTeam.name} 대 ${data.homeTeam.name}`}
                    >
                        VS
                    </div>

                    <HeroTeamIdentity
                        team={data.homeTeam}
                        align="left"
                    />
                </div>
            </div>
        </section>
    );
}

export default ScheduledMatchupHero;