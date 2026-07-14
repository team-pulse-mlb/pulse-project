import { useState } from 'react';

export interface HeroTeamIdentityData {
    name: string;
    abbr: string;
    logoUrl?: string | null;
}

interface HeroTeamIdentityProps {
    team: HeroTeamIdentityData;
    align: 'left' | 'right';
}

/**
 * 팀 심볼 이미지를 표시하는 원형 영역이다.
 *
 * 이미지 주소가 없거나 현재 주소의 이미지 로딩에 실패하면
 * 원 중앙에 팀 약어를 출력한다.
 */
function TeamSymbol({
                        team,
                    }: {
    team: HeroTeamIdentityData;
}) {
    /**
     * 단순 성공/실패 여부가 아니라 실패한 URL을 저장한다.
     *
     * API 재조회로 logoUrl이 바뀌면 기존 실패 URL과 달라지므로
     * 별도의 useEffect와 상태 초기화 없이 새 이미지를 다시 시도한다.
     */
    const [failedLogoUrl, setFailedLogoUrl] =
        useState<string | null>(null);

    const logoUrl = team.logoUrl ?? null;

    const canShowImage =
        logoUrl !== null &&
        failedLogoUrl !== logoUrl;

    return (
        <div className="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-full border border-white/20 bg-white/10 sm:h-16 sm:w-16">
            {canShowImage ? (
                <img
                    src={logoUrl}
                    alt={`${team.name} 심볼`}
                    className="h-full w-full object-contain p-1.5"
                    onError={() => {
                        setFailedLogoUrl(logoUrl);
                    }}
                />
            ) : (
                <span className="font-display text-sm font-bold text-white/90 sm:text-base">
          {team.abbr}
        </span>
            )}
        </div>
    );
}

/**
 * 팀 전체 이름을 크게 표시하고,
 * 그 아래에는 팀 약어를 작은 보조 글씨로 표시한다.
 */
function HeroTeamIdentity({
                              team,
                              align,
                          }: HeroTeamIdentityProps) {
    const isAwaySide = align === 'right';

    const textBlock = (
        <div
            className={`min-w-0 ${
                isAwaySide ? 'text-right' : 'text-left'
            }`}
        >
            <div className="max-w-[190px] font-display text-lg font-semibold leading-tight text-white sm:text-[22px]">
                {team.name}
            </div>

            <div className="mt-1.5 font-display text-xs font-semibold tracking-[0.08em] text-white/50 sm:text-[13px]">
                {team.abbr}
            </div>
        </div>
    );

    return (
        <div
            className={`flex min-w-0 items-center gap-3 ${
                isAwaySide
                    ? 'justify-end'
                    : 'justify-start'
            }`}
        >
            {isAwaySide ? (
                <>
                    {textBlock}
                    <TeamSymbol team={team} />
                </>
            ) : (
                <>
                    <TeamSymbol team={team} />
                    {textBlock}
                </>
            )}
        </div>
    );
}

export default HeroTeamIdentity;