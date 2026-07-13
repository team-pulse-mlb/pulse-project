import { Link } from 'react-router';

import BoxScoreTable from '../../../shared/components/BoxScoreTable';
import Card from '../../../shared/components/Card';
import HeroScoreboard from '../../../shared/components/HeroScoreboard';
import type { FinalGameDetailFixture } from '../fixtures/finalGameDetailFixture';
import type { DisplayMode } from '../lib/displayMode';
import EventTimeline, { type TimelineEvent } from './EventTimeline';
import ModeToggle from './ModeToggle';
import RecommendedSidebar from './RecommendedSidebar';
import TensionCurve from './TensionCurve';

interface FinalGameDetailProps {
    fixture: FinalGameDetailFixture;
    mode: DisplayMode;
    onModeChange: (mode: DisplayMode) => void;
}

function getHalfInningLabel(
    inning: number,
    inningType: 'TOP' | 'BOTTOM',
) {
    return `${inning}회 ${inningType === 'TOP' ? '초' : '말'}`;
}

function FinalGameDetail({
                             fixture,
                             mode,
                             onModeChange,
                         }: FinalGameDetailProps) {
    const isRevealed = mode === 'REVEALED';

    const headline = isRevealed
        ? fixture.headline.revealed
        : fixture.headline.protected;

    const tensionPoints = isRevealed
        ? fixture.revealedTensionPoints
        : fixture.protectedTensionPoints;

    /**
     * 보호 모드에서는 초·말 필드와 결과 문구를 전달하지 않는다.
     * 공개 모드에서만 공격 방향과 상세 결과 문구를 사용한다.
     */
    const timelineEvents: TimelineEvent[] = fixture.events.map(
        (event) => ({
            eventId: event.eventId,
            inning: event.inning,
            ...(isRevealed
                ? {
                    inningType: event.inningType,
                }
                : {}),
            text: isRevealed
                ? event.revealedText
                : event.protectedText,
            highlighted: event.highlighted ?? false,
        }),
    );

    return (
        <div className="mx-auto grid max-w-[1160px] grid-cols-1 items-start gap-10 px-4 py-7 sm:px-8 lg:grid-cols-[minmax(0,1fr)_336px]">
            <div className="flex min-w-0 flex-col gap-[18px]">
                <div className="flex items-center justify-between gap-4">
                    <Link
                        to="/"
                        className="inline-flex items-center gap-1.5 text-[15px] font-semibold text-text-muted transition-colors hover:text-mlb-navy"
                    >
                        <span aria-hidden="true">←</span>
                        뒤로
                    </Link>

                    <ModeToggle
                        mode={mode}
                        onChange={onModeChange}
                    />
                </div>

                <HeroScoreboard
                    status="final"
                    badgeLabel="종료"
                    subText={`${fixture.season} · ${fixture.dateLabel} · ${fixture.venue}`}
                    awayTeam={{
                        ...fixture.awayTeam,
                        score: isRevealed ? fixture.awayScore : null,
                    }}
                    homeTeam={{
                        ...fixture.homeTeam,
                        score: isRevealed ? fixture.homeScore : null,
                    }}
                >
                    {/* 헤드라인이 준비되지 않았다면 빈 영역도 렌더링하지 않는다. */}
                    {headline && (
                        <p className="mx-auto max-w-[620px] text-center text-sm font-medium leading-relaxed text-white/75 sm:text-[15px]">
                            {headline}
                        </p>
                    )}
                </HeroScoreboard>

                {/* 곡선 데이터가 없는 과거 경기는 영역 전체를 숨긴다. */}
                {tensionPoints.length > 0 && (
                    <TensionCurve
                        mode={mode}
                        points={tensionPoints}
                    />
                )}

                <EventTimeline
                    mode={mode}
                    events={timelineEvents}
                    title="주요 순간 타임라인"
                />

                {isRevealed && (
                    <>
                        <Card>
                            <h3 className="mb-5 text-[15px] font-bold text-text-strong">
                                이닝별 점수
                            </h3>

                            <BoxScoreTable
                                awayLine={fixture.awayLine}
                                homeLine={fixture.homeLine}
                            />
                        </Card>

                        <Card>
                            <h3 className="mb-5 text-[15px] font-bold text-text-strong">
                                득점 플레이
                            </h3>

                            {fixture.scoringPlays.length === 0 ? (
                                <p className="text-sm text-text-muted">
                                    기록된 득점 플레이가 없습니다.
                                </p>
                            ) : (
                                <ul>
                                    {fixture.scoringPlays.map((play) => (
                                        <li
                                            key={play.eventId}
                                            className="border-t border-divider py-4 first:border-t-0 first:pt-0 last:pb-0"
                                        >
                                            <div className="flex flex-wrap items-center justify-between gap-2">
                        <span className="font-display text-xs font-bold text-mlb-red">
                          {getHalfInningLabel(
                              play.inning,
                              play.inningType,
                          )}
                        </span>

                                                <span className="font-display text-sm font-semibold text-mlb-navy">
                          {play.scoreLabel}
                        </span>
                                            </div>

                                            <p className="mt-2 text-sm leading-relaxed text-text-body">
                                                {play.text}
                                            </p>
                                        </li>
                                    ))}
                                </ul>
                            )}
                        </Card>
                    </>
                )}
            </div>

            <aside className="lg:sticky lg:top-[86px]">
                <RecommendedSidebar currentGameId={fixture.gameId} />
            </aside>
        </div>
    );
}

export default FinalGameDetail;