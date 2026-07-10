import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';

import BoxScoreTable from '../../../shared/components/BoxScoreTable';
import Card from '../../../shared/components/Card';
import EmptyState from '../../../shared/components/EmptyState';
import GameCard from '../../../shared/components/GameCard';
import HeroScoreboard from '../../../shared/components/HeroScoreboard';
import InfoRow from '../../../shared/components/InfoRow';
import Reveal from '../../../shared/components/Reveal';
import SectionHeader from '../../../shared/components/SectionHeader';
import { formatStartTime } from '../../../shared/lib/format';
import CurrentSituationCard from '../components/CurrentSituationCard';
import EventTimeline from '../components/EventTimeline';
import ModeToggle from '../components/ModeToggle';
import TensionCurve from '../components/TensionCurve';
import { getGameDetailFixture, recommendedGameFixtures, type GameDetailFixture } from '../fixtures';
import { getStoredMode, storeMode, type DisplayMode } from '../lib/displayMode';

function BackButton() {
  const navigate = useNavigate();
  return <button type="button" onClick={() => navigate(-1)} className="text-sm font-semibold text-text-muted hover:text-text-strong">← 뒤로</button>;
}

function RecommendationList({ currentGameId }: { currentGameId: number }) {
  return (
    <div className="flex flex-col gap-3">
      {recommendedGameFixtures.filter((game) => game.gameId !== currentGameId).map((game) => (
        <GameCard key={game.gameId} game={game} variant="tile" />
      ))}
    </div>
  );
}

function GameHero({ fixture, mode }: { fixture: GameDetailFixture; mode: DisplayMode }) {
  const isScheduled = fixture.status === 'SCHEDULED';
  const isLive = fixture.status === 'LIVE';
  const isRevealed = mode === 'REVEALED';
  const status = isScheduled ? 'scheduled' : isLive ? 'live' : 'final';
  const inningType = fixture.inningType === 'TOP' ? '초' : '말';
  const headline = isRevealed ? fixture.revealedHeadline : fixture.protectedHeadline;
  const badgeLabel = isScheduled
    ? `예정 · ${formatStartTime(fixture.startTime)}`
    : isLive
      ? `LIVE · ${fixture.inning}회${isRevealed ? ` ${inningType}` : ''}`
      : '종료';

  return (
    <HeroScoreboard
      status={status}
      badgeLabel={badgeLabel}
      subText={fixture.venue}
      awayTeam={{ ...fixture.awayTeam, score: !isScheduled && isRevealed ? fixture.awayScore : null }}
      homeTeam={{ ...fixture.homeTeam, score: !isScheduled && isRevealed ? fixture.homeScore : null }}
    >
      {fixture.status === 'FINAL' && headline ? (
        <Reveal show>
          <p className="border-t border-white/10 pt-5 text-[15px] font-semibold leading-relaxed text-white">
            <span aria-hidden="true" className="mr-2 text-gold">★</span>{headline}
          </p>
        </Reveal>
      ) : null}
    </HeroScoreboard>
  );
}

function ScheduledBody({ fixture }: { fixture: GameDetailFixture }) {
  return (
    <Card>
      <h2 className="mb-4 text-[15px] font-bold text-text-strong">경기 정보</h2>
      <InfoRow label="구장" value={fixture.venue} />
      <InfoRow label="시작 시각" value={`${formatStartTime(fixture.startTime)} KST`} />
      <InfoRow label="선발" value={`${fixture.awayTeam.abbr} ${fixture.probablePitchers.away ?? '미확정'} · ${fixture.homeTeam.abbr} ${fixture.probablePitchers.home ?? '미확정'}`} />
    </Card>
  );
}

function CurrentMatchup({ fixture }: { fixture: GameDetailFixture }) {
  const matchup = fixture.currentMatchup;
  if (!matchup) return null;
  return (
    <p className="text-center text-sm text-text-body">
      현재 타석 — 타자 <strong className="font-semibold text-text-strong">{matchup.favorite === 'batter' && <span className="mr-1 text-gold">★</span>}{matchup.batter}</strong>
      {' '}vs 투수 <strong className="font-semibold text-text-strong">{matchup.favorite === 'pitcher' && <span className="mr-1 text-gold">★</span>}{matchup.pitcher}</strong>
    </p>
  );
}

function ScoreCard({ fixture }: { fixture: GameDetailFixture }) {
  return <Card><h2 className="mb-4 text-[15px] font-bold text-text-strong">이닝별 점수</h2><BoxScoreTable awayLine={fixture.awayLine} homeLine={fixture.homeLine} /></Card>;
}

function ScoringPlays({ fixture }: { fixture: GameDetailFixture }) {
  return (
    <Card>
      <h2 className="mb-4 text-[15px] font-bold text-text-strong">득점 장면</h2>
      <ul className="divide-y divide-divider">
        {fixture.scoringPlays.map((play) => (
          <li key={`${play.inningLabel}-${play.text}`} className="flex items-center gap-4 py-3 first:pt-0 last:pb-0">
            <span className="w-14 shrink-0 text-sm font-semibold text-text-muted">{play.inningLabel}</span>
            <span className="min-w-0 flex-1 text-[15px] text-text-body">{play.text}</span>
            <strong className="font-display text-sm text-mlb-navy">{play.score}</strong>
          </li>
        ))}
      </ul>
    </Card>
  );
}

function LiveBody({ fixture, mode }: { fixture: GameDetailFixture; mode: DisplayMode }) {
  const revealed = mode === 'REVEALED';
  return (
    <>
      <CurrentSituationCard situation={fixture.situation}>{revealed && fixture.situation ? <CurrentMatchup fixture={fixture} /> : null}</CurrentSituationCard>
      <EventTimeline events={revealed ? fixture.revealedEvents : fixture.protectedEvents} />
      {revealed && <ScoreCard fixture={fixture} />}
      {revealed && <EventTimeline title="최근 플레이" events={fixture.recentPlays} />}
    </>
  );
}

function FinalBody({ fixture, mode }: { fixture: GameDetailFixture; mode: DisplayMode }) {
  const revealed = mode === 'REVEALED';
  const curve = revealed ? fixture.revealedCurve : fixture.protectedCurve;
  return (
    <>
      {curve.length > 0 && <TensionCurve points={curve} />}
      <EventTimeline title="주요 순간 타임라인" events={revealed ? fixture.revealedEvents : fixture.protectedEvents} />
      {revealed && <ScoreCard fixture={fixture} />}
      {revealed && <ScoringPlays fixture={fixture} />}
    </>
  );
}

function GameDetailPage() {
  const { gameId } = useParams<{ gameId: string }>();
  const location = useLocation();
  const initialMode = gameId && !location.state?.fromCard ? getStoredMode(gameId) : 'PROTECTED';
  const [modeState, setModeState] = useState<{ gameId: string | undefined; value: DisplayMode }>({ gameId, value: initialMode });
  const mode = modeState.gameId === gameId ? modeState.value : initialMode;

  useEffect(() => {
    if (gameId && location.state?.fromCard) storeMode(gameId, 'PROTECTED');
  }, [gameId, location.state]);

  if (!gameId) return <div className="mx-auto max-w-[1160px] px-4 py-8"><EmptyState message="경기를 찾을 수 없습니다." /></div>;

  const fixture = getGameDetailFixture(gameId);
  const changeMode = (nextMode: DisplayMode) => { setModeState({ gameId, value: nextMode }); storeMode(gameId, nextMode); };

  return (
    <div className="mx-auto grid max-w-[1160px] grid-cols-1 gap-10 px-4 py-8 lg:grid-cols-[minmax(0,1fr)_336px]">
      <div className="flex min-w-0 flex-col gap-5">
        <div className="flex items-center justify-between gap-4"><BackButton />{fixture.status !== 'SCHEDULED' && <ModeToggle mode={mode} onChange={changeMode} />}</div>
        <GameHero fixture={fixture} mode={mode} />
        {fixture.status === 'SCHEDULED' && <ScheduledBody fixture={fixture} />}
        {fixture.status === 'LIVE' && <LiveBody fixture={fixture} mode={mode} />}
        {fixture.status === 'FINAL' && <FinalBody fixture={fixture} mode={mode} />}
        <section className="mt-3 lg:hidden"><SectionHeader title="다른 볼 만한 경기" /><RecommendationList currentGameId={Number(gameId)} /></section>
      </div>
      <aside className="hidden lg:block"><div className="sticky top-[86px]"><SectionHeader title="다른 볼 만한 경기" /><RecommendationList currentGameId={Number(gameId)} /></div></aside>
    </div>
  );
}

export default GameDetailPage;
