import Card from '../../../shared/components/Card';
import type { DisplayMode } from '../lib/displayMode';

export interface Situation {
    balls: number;
    strikes: number;
    outs: number;
    runnerOnFirst: boolean;
    runnerOnSecond: boolean;
    runnerOnThird: boolean;
    scoringPosition: boolean;
    basesLoaded: boolean;
}

export interface CurrentPlayer {
    id: number;
    name: string;
}

export interface CurrentMatchup {
    pitcher: CurrentPlayer;
    batter: CurrentPlayer;
}

interface CountDotsProps {
    label: 'B' | 'S' | 'O';
    count: number;
    max: number;
    activeClassName: string;
}

function CountDots({
                       label,
                       count,
                       max,
                       activeClassName,
                   }: CountDotsProps) {
    return (
        <div className="flex items-center gap-3">
      <span className="w-5 shrink-0 font-display text-sm font-bold text-text-muted">
        {label}
      </span>

            <div
                className="flex items-center gap-2"
                aria-label={`${label} ${count}`}
            >
                {Array.from({ length: max }).map((_, index) => {
                    const isActive = index < count;

                    return (
                        <span
                            key={`${label}-${index}`}
                            aria-hidden="true"
                            className={`h-3 w-3 shrink-0 rounded-full border ${
                                isActive
                                    ? `${activeClassName} border-transparent`
                                    : 'border-[#C9D0DB] bg-white'
                            }`}
                        />
                    );
                })}
            </div>
        </div>
    );
}

interface BaseProps {
    occupied: boolean;
    className: string;
}

/**
 * 1·2·3루 베이스다.
 * 주자가 있으면 빨간색으로 채우고, 없으면 흰색으로 표시한다.
 */
function Base({ occupied, className }: BaseProps) {
    return (
        <span
            aria-hidden="true"
            className={`absolute h-6 w-6 rotate-45 rounded-[4px] border-2 ${
                occupied
                    ? 'border-mlb-red bg-mlb-red shadow-[0_4px_10px_rgba(228,0,43,0.22)]'
                    : 'border-[#C9D0DB] bg-white'
            } ${className}`}
        />
    );
}

/**
 * 홈 베이스는 오각형으로 표현한다.
 */
function HomePlate() {
    const pentagonStyle = {
        clipPath:
            'polygon(0 0, 100% 0, 100% 58%, 50% 100%, 0 58%)',
    };

    return (
        <span
            aria-hidden="true"
            className="absolute bottom-0 left-1/2 h-7 w-7 -translate-x-1/2 bg-[#C9D0DB]"
            style={pentagonStyle}
        >
      <span
          className="absolute inset-[2px] bg-white"
          style={pentagonStyle}
      />
    </span>
    );
}

interface BaseGraphicProps {
    situation: Situation;
}

/**
 * 고정된 그래픽 영역 안에서만 베이스를 배치한다.
 *
 * 부모 카드 너비가 좁아지면 카운트와 베이스가 세로로 배치되므로,
 * 베이스가 카드 경계를 벗어나거나 옆 영역과 겹치지 않는다.
 */
function BaseGraphic({ situation }: BaseGraphicProps) {
    return (
        <div
            className="relative h-[104px] w-[112px] shrink-0"
            role="img"
            aria-label={[
                situation.runnerOnFirst
                    ? '1루 주자 있음'
                    : '1루 주자 없음',
                situation.runnerOnSecond
                    ? '2루 주자 있음'
                    : '2루 주자 없음',
                situation.runnerOnThird
                    ? '3루 주자 있음'
                    : '3루 주자 없음',
            ].join(', ')}
        >
            <Base
                occupied={situation.runnerOnSecond}
                className="left-1/2 top-1 -translate-x-1/2"
            />

            <Base
                occupied={situation.runnerOnThird}
                className="left-2 top-[39px]"
            />

            <Base
                occupied={situation.runnerOnFirst}
                className="right-2 top-[39px]"
            />

            <HomePlate />
        </div>
    );
}

interface CurrentSituationCardProps {
    /** 이닝 교대 등 현재 상황이 없으면 null이다. */
    situation: Situation | null;

    /**
     * 공개 응답에서 받은 현재 타석 정보다.
     * 보호 모드에서는 null을 전달해 선수명이 DOM에 생성되지 않게 한다.
     */
    matchup?: CurrentMatchup | null;

    mode: DisplayMode;
}

function CurrentSituationCard({
                                  situation,
                                  matchup = null,
                                  mode,
                              }: CurrentSituationCardProps) {
    const isRevealed = mode === 'REVEALED';

    return (
        <Card>
            <h3 className="mb-5 text-[15px] font-bold text-text-strong">
                현재 상황
            </h3>

            {situation === null ? (
                <div className="rounded-panel bg-divider px-5 py-8 text-center text-sm text-text-muted">
                    이닝 교대 중입니다.
                </div>
            ) : (
                <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(220px,280px)]">
                    {/* 카운트·득점권·베이스는 보호/공개 공통 정보다. */}
                    <section className="min-w-0 rounded-panel border border-card-border bg-white p-5">
                        <div className="mb-5 flex flex-wrap items-center gap-2">
                            {situation.basesLoaded ? (
                                <span className="rounded-full bg-red-tint px-3 py-1 text-xs font-bold text-mlb-red">
                  만루
                </span>
                            ) : situation.scoringPosition ? (
                                <span className="rounded-full bg-red-tint px-3 py-1 text-xs font-bold text-mlb-red">
                  득점권 주자 있음
                </span>
                            ) : (
                                <span className="rounded-full bg-divider px-3 py-1 text-xs font-semibold text-text-muted">
                  득점권 주자 없음
                </span>
                            )}

                            <span className="text-xs text-text-faint">
                현재 카운트와 주자 상황
              </span>
                        </div>

                        <div className="grid grid-cols-1 items-center gap-5 sm:grid-cols-[minmax(120px,1fr)_112px]">
                            <div className="flex flex-col gap-3">
                                <CountDots
                                    label="B"
                                    count={situation.balls}
                                    max={3}
                                    activeClassName="bg-dot-ball"
                                />

                                <CountDots
                                    label="S"
                                    count={situation.strikes}
                                    max={2}
                                    activeClassName="bg-dot-strike"
                                />

                                <CountDots
                                    label="O"
                                    count={situation.outs}
                                    max={2}
                                    activeClassName="bg-dot-out"
                                />
                            </div>

                            <div className="flex justify-center sm:justify-end">
                                <BaseGraphic situation={situation} />
                            </div>
                        </div>
                    </section>

                    {/* 현재 선수명은 공개 모드에서만 렌더링한다. */}
                    <section className="min-w-0 rounded-panel bg-red-tint-soft p-5">
            <span className="text-xs font-semibold text-text-muted">
              현재 타석
            </span>

                        {isRevealed && matchup ? (
                            <div className="mt-5 grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-3 text-center">
                                <div className="min-w-0">
                                    <div className="text-xs font-medium text-text-muted">
                                        투수
                                    </div>

                                    <div
                                        className="mt-2 break-words font-display text-lg font-semibold leading-tight text-text-strong"
                                        title={matchup.pitcher.name}
                                    >
                                        {matchup.pitcher.name}
                                    </div>
                                </div>

                                <span
                                    aria-hidden="true"
                                    className="font-display text-xs font-bold text-text-faint"
                                >
                  VS
                </span>

                                <div className="min-w-0">
                                    <div className="text-xs font-medium text-text-muted">
                                        타자
                                    </div>

                                    <div
                                        className="mt-2 break-words font-display text-lg font-semibold leading-tight text-text-strong"
                                        title={matchup.batter.name}
                                    >
                                        {matchup.batter.name}
                                    </div>
                                </div>
                            </div>
                        ) : isRevealed ? (
                            <p className="mt-4 text-sm leading-relaxed text-text-muted">
                                현재 타석 정보가 없습니다.
                            </p>
                        ) : (
                            <p className="mt-4 text-sm leading-relaxed text-text-muted">
                                보호 모드에서는 현재 타자와 투수 정보를 숨깁니다.
                            </p>
                        )}
                    </section>
                </div>
            )}
        </Card>
    );
}

export default CurrentSituationCard;