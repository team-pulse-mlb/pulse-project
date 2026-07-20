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
 * 주자가 있으면 노란색과 주황색이 섞인 점등 효과로 표시하고,
 * 주자가 없으면 흰색으로 유지한다.
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
                    ? 'border-[#FACC15] bg-[linear-gradient(135deg,#FFF7AE_0%,#FFE55C_52%,#FBBF24_100%)] shadow-[0_2px_6px_rgba(249,115,22,0.3)]'
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

function normalizePlayerName(
    name: string,
): string {
    return name
        .trim()
        .toLowerCase();
}

function isFavoritePlayer(
    playerName: string,
    favoritePlayerNames: string[],
): boolean {
    const normalizedPlayerName =
        normalizePlayerName(playerName);

    return favoritePlayerNames.some(
        (favoritePlayerName) =>
            normalizePlayerName(favoritePlayerName)
            === normalizedPlayerName,
    );
}

/**
 * 관심 선수 표시 방식은 경기 흐름 리스트의 별표 표시와 맞춘다.
 * 보호 모드에서는 선수명을 렌더링하지 않으므로 이 표시도 노출되지 않는다.
 */
function FavoritePlayerMark() {
    return (
        <span
            aria-label="관심 선수"
            title="관심 선수"
            className="ml-1 inline-flex align-middle text-gold"
        >
            ★
        </span>
    );
}

interface CurrentMatchupPanelProps {
    matchup?: CurrentMatchupViewModel | null;
    isRevealed: boolean;
    favoritePlayerNames: string[];
}

/**
 * 현재 타석 선수명은 공개 모드에서만 표시한다.
 * 관심 선수 이름과 일치하면 기존 경기 흐름 영역과 같은 별표를 이름 옆에 붙인다.
 */
function CurrentMatchupPanel({
                                 matchup = null,
                                 isRevealed,
                                 favoritePlayerNames,
                             }: CurrentMatchupPanelProps) {
    if (!isRevealed) {
        return (
            <p className="mt-4 text-sm leading-relaxed text-text-muted">
                보호 모드에서는 현재 타자와 투수 정보를 숨깁니다.
            </p>
        );
    }

    if (!matchup) {
        return (
            <p className="mt-4 text-sm leading-relaxed text-text-muted">
                현재 타석 정보가 없습니다.
            </p>
        );
    }

    const isFavoritePitcher =
        isFavoritePlayer(
            matchup.pitcher.name,
            favoritePlayerNames,
        );

    const isFavoriteBatter =
        isFavoritePlayer(
            matchup.batter.name,
            favoritePlayerNames,
        );

    return (
        <div className="mt-4 grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] items-center gap-3 text-center">
            <div className="min-w-0">
                <div className="text-xs font-medium text-text-muted">
                    투수
                </div>

                <div
                    className="mt-1 break-words font-display text-base font-semibold leading-tight text-text-strong"
                    title={
                        matchup.pitcher
                            .name
                    }
                >
                    {
                        matchup.pitcher
                            .name
                    }

                    {isFavoritePitcher ? (
                        <FavoritePlayerMark />
                    ) : null}
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
                    className="mt-1 break-words font-display text-base font-semibold leading-tight text-text-strong"
                    title={
                        matchup.batter
                            .name
                    }
                >
                    {
                        matchup.batter
                            .name
                    }

                    {isFavoriteBatter ? (
                        <FavoritePlayerMark />
                    ) : null}
                </div>
            </div>
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

    /**
     * 현재 타석 선수가 관심 선수이면 공개 모드에서 이름 옆에 별표를 표시한다.
     */
    favoritePlayerNames?: string[];
}

function CurrentSituationCard({
                                  situation,
                                  matchup = null,
                                  mode,
                                  favoritePlayerNames = [],
                              }: CurrentSituationCardProps) {
    const isRevealed =
        mode === 'REVEALED';

    return (
        <Card>
            {situation === null ? (
                <div className="rounded-panel bg-divider px-5 py-6 text-center text-sm text-text-muted">
                    현재 상황 정보가 없습니다.
                </div>
            ) : (
                <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_auto]">
                    <section className="min-w-0 rounded-panel bg-red-tint-soft p-4">
                        <span className="text-xs font-semibold text-text-muted">
                            현재 타석
                        </span>
                        <CurrentMatchupPanel
                            matchup={
                                matchup
                            }
                            isRevealed={
                                isRevealed
                            }
                            favoritePlayerNames={
                                favoritePlayerNames
                            }
                        />
                    </section>
                    <section className="min-w-0 rounded-panel border border-card-border bg-white p-4 lg:w-fit">
                        <div className="mb-3 flex flex-wrap items-center gap-2">
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

                        </div>

                        {/*
                        * B/S/O와 주자 위치는 보호 모드에서도 허용되는 상황 정보다.
                        * 공개 여부와 상관없이 실제 situation 값을 사용하고,
                        * 선수명이 포함되는 현재 타석 정보만 보호 모드에서 숨긴다.
                        */}
                        <div className="flex flex-col items-center justify-center gap-3 sm:flex-row sm:gap-10">
                            <div className="flex flex-col gap-2.5">
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
                    </section>
                </div>
            )}
        </Card>
    );
}

export default CurrentSituationCard;