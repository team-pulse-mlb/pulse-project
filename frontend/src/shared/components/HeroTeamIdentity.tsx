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
 * 팀 심볼 이미지를 표시하는 영역이다.
 */
function TeamSymbol({
                        team,
                    }: {
    team: HeroTeamIdentityData;
}) {
    /**
     * 실패한 로고 URL을 기억해 같은 실패 URL을 계속 재시도하지 않는다.
     *
     * API 재조회로 logoUrl이 바뀌면 실패 URL도 달라지므로
     * 별도 초기화 없이 새 로고 로딩을 다시 시도할 수 있다.
     */
    const [failedLogoUrl, setFailedLogoUrl] =
        useState<string | null>(null);

    const logoUrl = team.logoUrl ?? null;

    const canShowImage =
        logoUrl !== null &&
        failedLogoUrl !== logoUrl;

    /**
     * 로고 사이즈
     */
    const symbolSizeClass =
        'h-[60px] w-[60px] sm:h-[68px] sm:w-[68px]';

    if (canShowImage) {
        return (
            <div
                className={`flex shrink-0 items-center justify-center ${symbolSizeClass}`}
            >
                <img
                    src={logoUrl}
                    alt={`${team.name} 로고`}
                    className="h-full w-full object-contain"
                    onError={() => {
                        setFailedLogoUrl(logoUrl);
                    }}
                />
            </div>
        );
    }

    /**
     * 로고가 없거나 로딩에 실패한 경우에는
     * 약어가 들어간 원형 fallback도 노출하지 않는다.
     *
     * 팀 약어는 이름 아래 보조 텍스트로 이미 표시되므로,
     * 로고 영역에서 중복 심볼을 만들지 않는다.
     */
    return null;
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

    return (
        <div
            className={`flex min-w-0 items-center gap-3 sm:gap-4 ${
                isAwaySide ? 'flex-row' : 'flex-row-reverse'
            }`}
        >
            <div
                className={`min-w-0 ${
                    isAwaySide ? 'text-right' : 'text-left'
                }`}
            >
                <p className="line-clamp-2 font-display text-[20px] font-semibold leading-[1.05] tracking-[-0.02em] text-white sm:text-[22px]">
                    {team.name}
                </p>

                <p className="mt-1 text-[13px] font-semibold tracking-[0.08em] text-white/65">
                    {team.abbr}
                </p>
            </div>

            <TeamSymbol team={team} />
        </div>
    );
}

export default HeroTeamIdentity;