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
  side: 'away' | 'home';
}

function TeamMark({ team, size, tone, side }: TeamMarkProps) {
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
  const alignmentStyle = side === 'away'
    ? 'justify-end'
    : 'justify-start';
  const symbolOrderStyle = side === 'away'
    ? 'order-2'
    : '';
  const labelOrderStyle = side === 'away'
    ? 'order-1 text-right'
    : '';

  return (
    <span
      className={`flex w-full min-w-0 items-center gap-1.5 sm:gap-2 ${alignmentStyle}`}
      title={team.name}
    >
      {canShowLogo ? (
        <img
          src={logoUrl}
          alt=""
          aria-hidden="true"
          className={`${symbolSize} shrink-0 object-contain ${symbolOrderStyle}`}
          onError={() => setFailedLogoUrl(logoUrl)}
        />
      ) : (
        <span
          aria-hidden="true"
          className={`flex shrink-0 items-center justify-center rounded-full border font-display font-bold ${symbolSize} ${fallbackStyle} ${symbolOrderStyle}`}
        >
          {team.abbreviation.slice(0, 1)}
        </span>
      )}
      <span className={`min-w-0 truncate font-display font-bold leading-none ${labelStyle} ${labelOrderStyle}`}>
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
  className?: string;
}

function TeamMatchup({
  awayTeam,
  homeTeam,
  size = 'compact',
  tone = 'light',
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

  return (
    <span
      className={`grid w-full min-w-0 grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-3 sm:gap-4 ${toneStyle} ${className}`}
      aria-label={`${awayTeam.name} 대 ${homeTeam.name}`}
    >
      <TeamMark team={awayTeam} size={size} tone={tone} side="away" />
      <span
        aria-hidden="true"
        className={`shrink-0 font-display font-semibold ${versusStyle} ${versusTone}`}
      >
        VS
      </span>
      <TeamMark team={homeTeam} size={size} tone={tone} side="home" />
    </span>
  );
}

export default TeamMatchup;
