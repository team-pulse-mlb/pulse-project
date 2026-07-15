import { useState } from 'react';

export interface TeamIdentityData {
  abbreviation: string;
  name: string;
  logoUrl?: string | null;
}

interface TeamMarkProps {
  team: TeamIdentityData;
  size: 'compact' | 'hero';
  tone: 'light' | 'dark';
}

function TeamMark({ team, size, tone }: TeamMarkProps) {
  const [failedLogoUrl, setFailedLogoUrl] = useState<string | null>(null);
  const logoUrl = team.logoUrl ?? null;
  const canShowLogo = logoUrl !== null && failedLogoUrl !== logoUrl;
  const symbolSize = size === 'hero'
    ? 'h-10 w-10 sm:h-16 sm:w-16'
    : 'h-7 w-7';
  const labelStyle = size === 'hero'
    ? 'text-[28px] sm:text-[44px]'
    : 'text-[15px]';
  const fallbackStyle = tone === 'dark'
    ? 'border-white/20 bg-white/10 text-white/90'
    : 'border-card-border bg-page text-text-strong';

  return (
    <span
      className="inline-flex min-w-0 items-center gap-1.5 sm:gap-2"
      title={team.name}
    >
      {canShowLogo ? (
        <img
          src={logoUrl}
          alt=""
          aria-hidden="true"
          className={`${symbolSize} shrink-0 object-contain`}
          onError={() => setFailedLogoUrl(logoUrl)}
        />
      ) : (
        <span
          aria-hidden="true"
          className={`flex shrink-0 items-center justify-center rounded-full border font-display font-bold ${symbolSize} ${fallbackStyle}`}
        >
          {team.abbreviation.slice(0, 1)}
        </span>
      )}
      <span className={`truncate font-display font-bold leading-none ${labelStyle}`}>
        {team.abbreviation}
      </span>
    </span>
  );
}

interface TeamMatchupProps {
  awayTeam: TeamIdentityData;
  homeTeam: TeamIdentityData;
  size?: 'compact' | 'hero';
  tone?: 'light' | 'dark';
  align?: 'start' | 'center';
  className?: string;
}

function TeamMatchup({
  awayTeam,
  homeTeam,
  size = 'compact',
  tone = 'light',
  align = 'center',
  className = '',
}: TeamMatchupProps) {
  const versusStyle = size === 'hero'
    ? 'text-[16px] sm:text-[26px]'
    : 'text-[13px]';
  const toneStyle = tone === 'dark'
    ? 'text-white'
    : 'text-text-strong';
  const versusTone = tone === 'dark'
    ? 'text-white/40'
    : 'text-text-faint';
  const alignmentStyle = align === 'start'
    ? 'justify-start'
    : 'justify-center';

  return (
    <span
      className={`flex min-w-0 items-center gap-2 sm:gap-3 ${alignmentStyle} ${toneStyle} ${className}`}
      aria-label={`${awayTeam.name} 대 ${homeTeam.name}`}
    >
      <TeamMark team={awayTeam} size={size} tone={tone} />
      <span
        aria-hidden="true"
        className={`shrink-0 font-display font-semibold lowercase ${versusStyle} ${versusTone}`}
      >
        vs
      </span>
      <TeamMark team={homeTeam} size={size} tone={tone} />
    </span>
  );
}

export default TeamMatchup;
