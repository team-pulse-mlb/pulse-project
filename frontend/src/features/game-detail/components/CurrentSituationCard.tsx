import Card from '../../../shared/components/Card';
import type { DisplayMode } from '../lib/displayMode';
import type {
    CurrentMatchupViewModel,
    GameSituationViewModel,
} from '../model/gameDetailViewModels';

interface CountDotsProps {
    label: 'B' | 'S' | 'O';
    count: number | null;
    max: number;
    activeClassName: string;
}

function CountDots({
                       label,
                       count,
                       max,
                       activeClassName,
                   }: CountDotsProps) {
    const hasCount =
        count !== null
        && Number.isInteger(count)
        && count >= 0;

    const safeCount =
        hasCount
            ? Math.min(count, max)
            : 0;

    return (
        <div className="flex items-center gap-3">
            <span className="w-5 shrink-0 font-display text-sm font-bold text-text-muted">
                {label}
            </span>

            {hasCount ? (
                <div
                    className="flex items-center gap-2"
                    aria-label={`${label} ${count}`}
                >
                    {Array.from({ length: max }).map(
                        (_, index) => {
                            const isActive =
                                index < safeCount;

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
                        },
                    )}
                </div>
            ) : (
                <span
                    className="font-display text-sm font-semibold text-text-faint"
                    aria-label={`${label} 정보 없음`}
                >
                    -
                </span>
            )}
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
function Base({
                  occupied,
                  className,
              }: BaseProps) {
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
    situation: GameSituationViewModel;
}

/**
 * 고정된 그래픽 영역 안에서만 베이스를 배치한다.
 */
function BaseGraphic({
                         situation,
                     }: BaseGraphicProps) {
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
                occupied={
                    situation.runnerOnSecond
                }
                className="left-1/2 top-1 -translate-x-1/2"
            />

            <Base
                occupied={
                    situation.runnerOnThird
                }
                className="left-2 top-[39px]"
            />

            <Base
                occupied={
                    situation.runnerOnFirst
                }
                className="right-2 top-[39px]"
            />

            <HomePlate />
        </div>
    );
}

/**
 * 보호 모드에서 실제 카운트 값을 사용하지 않고
 * 영역의 형태만 보여주는 고정 미리보기다.
 */
function ProtectedCountPreview() {
    const rows = [
        {
            label: 'B',
            max: 3,
            activeClassName: 'bg-dot-ball',
        },
        {
            label: 'S',
            max: 2,
            activeClassName: 'bg-dot-strike',
        },
        {
            label: 'O',
            max: 2,
            activeClassName: 'bg-dot-out',
        },
    ] as const;

    return (
        <div
            aria-hidden="true"
            className="flex select-none flex-col gap-3 opacity-55 blur-[3px]"
        >
            {rows.map((row) => (
                <div
                    key={row.label}
                    className="flex items-center gap-3"
                >
                    <span className="w-5 shrink-0 font-display text-sm font-bold text-text-muted">
                        {row.label}
                    </span>

                    <div className="flex items-center gap-2">
                        {Array.from({
                            length: row.max,
                        }).map((_, index) => (
                            <span
                                key={`${row.label}-protected-${index}`}
                                className={`h-3 w-3 shrink-0 rounded-full border border-transparent ${
                                    index === 0
                                        ? row.activeClassName
                                        : 'bg-[#D9DEE7]'
                                }`}
                            />
                        ))}
                    </div>
                </div>
            ))}
        </div>
    );
}

/**
 * 보호 모드에서는 실제 주자 점유 상태를 전달하지 않고
 * 베이스 영역의 형태만 보여주는 고정 미리보기를 사용한다.
 */
function ProtectedBasePreview() {
    return (
        <div
            aria-hidden="true"
            className="relative h-[104px] w-[112px] shrink-0 select-none opacity-55 blur-[3px]"
        >
            <Base
                occupied={false}
                className="left-1/2 top-1 -translate-x-1/2"
            />

            <Base
                occupied={true}
                className="left-2 top-[39px]"
            />

            <Base
                occupied={false}
                className="right-2 top-[39px]"
            />

            <HomePlate />
        </div>
    );
}

interface CurrentSituationCardProps {
    /**
     * 이닝 교대 중이거나 현재 상황 데이터가 없으면 null이다.
     */
    situation: GameSituationViewModel | null;

    /**
     * 현재 타자·투수는 공개 응답에서만 전달한다.
     */
    matchup?: CurrentMatchupViewModel | null;

    mode: DisplayMode;
}

function CurrentSituationCard({
                                  situation,
                                  matchup = null,
                                  mode,
                              }: CurrentSituationCardProps) {
    const isRevealed =
        mode === 'REVEALED';

    const isProtected =
        mode === 'PROTECTED';

    return (
        <Card>
            <h3 className="mb-5 text-[15px] font-bold text-text-strong">
                현재 상황
            </h3>

            {situation === null ? (
                <div className="rounded-panel bg-divider px-5 py-8 text-center text-sm text-text-muted">
                    현재 상황 정보가 없습니다.
                </div>
            ) : (
                <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(220px,280px)]">
                    <section className="min-w-0 rounded-panel border border-card-border bg-white p-5">
                        <div className="mb-5 flex flex-wrap items-center gap-2">
                            {isProtected ? (
                                <span className="rounded-full bg-divider px-3 py-1 text-xs font-semibold text-text-muted">
                                    상황 보호 중
                                </span>
                            ) : situation.basesLoaded ? (
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
                                {isProtected
                                    ? '공개 모드에서 현재 상황을 확인할 수 있습니다.'
                                    : '현재 카운트와 주자 상황'}
                            </span>
                        </div>

                        {isProtected ? (
                            <div
                                className="relative"
                                aria-label="보호 모드에서는 현재 카운트와 주자 상황을 숨깁니다."
                            >
                                {/*
                                 * 실제 situation 값은 렌더링하지 않는다.
                                 * 고정된 자리 표시자만 흐리게 보여서
                                 * 영역의 용도만 알 수 있도록 한다.
                                 */}
                                <div className="grid grid-cols-1 items-center gap-5 sm:grid-cols-[minmax(120px,1fr)_112px]">
                                    <ProtectedCountPreview />

                                    <div className="flex justify-center sm:justify-end">
                                        <ProtectedBasePreview />
                                    </div>
                                </div>

                                <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
                                    <span className="rounded-full border border-card-border bg-white/90 px-3 py-1 text-xs font-semibold text-text-muted shadow-sm">
                                        보호 중
                                    </span>
                                </div>
                            </div>
                        ) : (
                            <div className="grid grid-cols-1 items-center gap-5 sm:grid-cols-[minmax(120px,1fr)_112px]">
                                <div className="flex flex-col gap-3">
                                    <CountDots
                                        label="B"
                                        count={
                                            situation.balls
                                        }
                                        max={3}
                                        activeClassName="bg-dot-ball"
                                    />

                                    <CountDots
                                        label="S"
                                        count={
                                            situation.strikes
                                        }
                                        max={2}
                                        activeClassName="bg-dot-strike"
                                    />

                                    <CountDots
                                        label="O"
                                        count={
                                            situation.outs
                                        }
                                        max={2}
                                        activeClassName="bg-dot-out"
                                    />
                                </div>

                                <div className="flex justify-center sm:justify-end">
                                    <BaseGraphic
                                        situation={
                                            situation
                                        }
                                    />
                                </div>
                            </div>
                        )}
                    </section>

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
                                        title={
                                            matchup.pitcher
                                                .name
                                        }
                                    >
                                        {
                                            matchup.pitcher
                                                .name
                                        }
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
                                        title={
                                            matchup.batter
                                                .name
                                        }
                                    >
                                        {
                                            matchup.batter
                                                .name
                                        }
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