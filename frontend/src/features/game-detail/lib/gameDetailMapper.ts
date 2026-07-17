import { formatStartTime } from '../../../shared/lib/format';
import type {
    ApiInningType,
    CurrentMatchupResponse,
    FinalGameDetailResponse,
    GameDetailResponse,
    InningScoresResponse,
    LiveGameDetailResponse,
    ScheduledGameDetailResponse,
    SituationResponse,
    TeamResponse,
} from '../api/gameDetailTypes';
import type {
    CurrentMatchupViewModel,
    FinalGameDetailViewModel,
    GameDateViewModel,
    GameDetailTeamViewModel,
    GameDetailViewModel,
    GameInningScoresViewModel,
    GameSituationViewModel,
    LiveGameDetailViewModel,
    ScheduledGameDetailViewModel,
    TensionLevel,
    ViewInningType,
} from '../model/gameDetailViewModels';

/**
 * API 팀 정보를 화면에서 사용하는 형태로 변환한다.
 *
 * 팀 이름이나 약어가 없는 경우에도 화면 렌더링이 중단되지 않도록
 * 사용할 수 있는 값을 순서대로 폴백한다.
 */
function toTeamViewModel(
    team: TeamResponse,
): GameDetailTeamViewModel {
    const abbr =
        team.abbr?.trim()
        || 'TBD';

    const name =
        team.name?.trim()
        || team.abbr?.trim()
        || '팀 정보 없음';

    return {
        name,
        abbr,

        /*
         * 백엔드에서 내려준 MLB 공식 팀 로고 URL을 사용한다.
         * URL이 없거나 이미지 로딩에 실패하면 HeroTeamIdentity가
         * 기존처럼 팀 약어를 대신 표시한다.
         */
        logoUrl:
            team.logoUrl?.trim()
            || null,
    };
}

/**
 * 백엔드의 Top/Bottom 값을 화면에서 사용하는
 * TOP/BOTTOM 값으로 정규화한다.
 */
function normalizeInningType(
    inningType: ApiInningType | null,
): ViewInningType | null {
    if (inningType === null) {
        return null;
    }

    const normalized =
        inningType.toUpperCase();

    if (normalized === 'TOP') {
        return 'TOP';
    }

    if (normalized === 'BOTTOM') {
        return 'BOTTOM';
    }

    return null;
}

/**
 * 경기 흐름 단계는 화면 계약상 1~5만 허용한다.
 *
 * API에서 범위를 벗어난 값이 오더라도 임의로 보정하지 않고
 * 해당 그래프 포인트를 제외한다.
 */
function normalizeTensionLevel(
    level: number | null,
): TensionLevel | null {
    if (
        level === 1
        || level === 2
        || level === 3
        || level === 4
        || level === 5
    ) {
        return level;
    }

    return null;
}

/**
 * ISO 시작 시각을 사용자 로컬 시간대 기준의
 * 화면 표시용 날짜 정보로 변환한다.
 */
function toGameDateViewModel(
    startTime: string | null,
): GameDateViewModel {
    if (!startTime) {
        return {
            season: null,
            dateLabel: null,
            startTimeLabel: null,
        };
    }

    const date = new Date(startTime);

    if (Number.isNaN(date.getTime())) {
        return {
            season: null,
            dateLabel: null,
            startTimeLabel: null,
        };
    }

    return {
        season: date.getFullYear(),

        dateLabel:
            `${date.getMonth() + 1}/${date.getDate()}`,

        startTimeLabel:
            formatStartTime(startTime),
    };
}

/**
 * 현재 카운트와 주자 상황을 화면 모델로 변환한다.
 *
 * 이닝 교대 중이거나 상황 데이터가 없으면 null을 유지한다.
 */
function toSituationViewModel(
    situation: SituationResponse | null,
): GameSituationViewModel | null {
    if (situation === null) {
        return null;
    }

    return {
        balls: situation.balls,
        strikes: situation.strikes,
        outs: situation.outs,

        runnerOnFirst:
        situation.runnerOnFirst,

        runnerOnSecond:
        situation.runnerOnSecond,

        runnerOnThird:
        situation.runnerOnThird,

        scoringPosition:
        situation.scoringPosition,

        basesLoaded:
        situation.basesLoaded,
    };
}

/**
 * 공개 응답의 현재 타자·투수 정보를 화면 모델로 변환한다.
 */
function toCurrentMatchupViewModel(
    matchup: CurrentMatchupResponse | null,
): CurrentMatchupViewModel | null {
    if (matchup === null) {
        return null;
    }

    return {
        batter: {
            id: matchup.batter.id,
            name: matchup.batter.name,
        },

        pitcher: {
            id: matchup.pitcher.id,
            name: matchup.pitcher.name,
        },
    };
}

/**
 * 이닝별 득점과 총 득점만 점수판 모델로 변환한다.
 *
 * 경기 상세 API가 제공하지 않는 H·E 값은
 * 타입과 화면에서 모두 제거했다.
 */
function toInningScoresViewModel(
    inningScores: InningScoresResponse,
    awayAbbr: string,
    homeAbbr: string,
    awayRuns: number | null,
    homeRuns: number | null,
): GameInningScoresViewModel {
    return {
        awayLine: {
            abbr: awayAbbr,
            innings: [...inningScores.away],
            runs: awayRuns,
        },

        homeLine: {
            abbr: homeAbbr,
            innings: [...inningScores.home],
            runs: homeRuns,
        },
    };
}

/**
 * 예정 경기 응답을 화면 모델로 변환한다.
 *
 * 예정 경기는 공개할 결과가 없으므로
 * displayMode는 항상 PROTECTED다.
 */
export function toScheduledGameDetailViewModel(
    response: ScheduledGameDetailResponse,
): ScheduledGameDetailViewModel {
    return {
        kind: 'SCHEDULED',

        gameId: response.gameId,
        status: response.status,
        displayMode: 'PROTECTED',

        homeTeam:
            toTeamViewModel(
                response.homeTeam,
            ),

        awayTeam:
            toTeamViewModel(
                response.awayTeam,
            ),

        ...toGameDateViewModel(
            response.startTime,
        ),

        venue:
            response.venue?.trim()
            || null,

        probablePitchers: {
            home:
                response.probablePitchers
                    .home
                    ?.trim()
                || null,

            away:
                response.probablePitchers
                    .away
                    ?.trim()
                || null,
        },
    };
}

/**
 * 진행 경기 보호·공개 응답을 화면 모델로 변환한다.
 */
export function toLiveGameDetailViewModel(
    response: LiveGameDetailResponse,
): LiveGameDetailViewModel {
    const homeTeam =
        toTeamViewModel(
            response.homeTeam,
        );

    const awayTeam =
        toTeamViewModel(
            response.awayTeam,
        );

    const isRevealed =
        response.displayMode === 'REVEALED';

    const homeScore =
        isRevealed
            ? response.score.home
            : null;

    const awayScore =
        isRevealed
            ? response.score.away
            : null;

    return {
        kind: 'LIVE',

        gameId: response.gameId,
        status: response.status,
        displayMode: response.displayMode,

        homeTeam,
        awayTeam,

        ...toGameDateViewModel(
            response.startTime,
        ),

        venue:
            response.displayMode === 'REVEALED'
                ? response.venue?.trim()
                    || null
                : null,

        inning: response.inning,

        /*
         * 보호 응답에는 초·말 필드가 존재하지 않는다.
         */
        inningType:
            isRevealed
                ? normalizeInningType(
                    response.inningType,
                )
                : null,

        homeScore,
        awayScore,

        situation:
            toSituationViewModel(
                response.situation,
            ),

        currentMatchup:
            isRevealed
                ? toCurrentMatchupViewModel(
                    response.currentMatchup,
                )
                : null,

        inningScores:
            isRevealed
                ? toInningScoresViewModel(
                    response.inningScores,
                    awayTeam.abbr,
                    homeTeam.abbr,
                    awayScore,
                    homeScore,
                )
                : null,

        favoritePlayersPlaying: [
            ...response.favoritePlayersPlaying,
        ],
    };
}

/**
 * 종료 경기 보호·공개 응답을 화면 모델로 변환한다.
 */
export function toFinalGameDetailViewModel(
    response: FinalGameDetailResponse,
): FinalGameDetailViewModel {
    const homeTeam =
        toTeamViewModel(
            response.homeTeam,
        );

    const awayTeam =
        toTeamViewModel(
            response.awayTeam,
        );

    const isRevealed =
        response.displayMode === 'REVEALED';

    const homeScore =
        isRevealed
            ? response.finalScore.home
            : null;

    const awayScore =
        isRevealed
            ? response.finalScore.away
            : null;

    /**
     * 보호 그래프 포인트에는 inningType이 없고,
     * 공개 포인트에만 inningType이 존재한다.
     *
     * in 연산자로 합집합 타입을 안전하게 좁힌다.
     */
    const tensionPoints =
        response.tensionCurve.flatMap(
            (point) => {
                const level =
                    normalizeTensionLevel(
                        point.level,
                    );

                if (
                    point.inning === null
                    || level === null
                ) {
                    return [];
                }

                if ('inningType' in point) {
                    const inningType =
                        normalizeInningType(
                            point.inningType,
                        );

                    return [
                        {
                            inning: point.inning,

                            ...(inningType
                                ? {
                                    inningType,
                                }
                                : {}),

                            level,
                        },
                    ];
                }

                return [
                    {
                        inning: point.inning,
                        level,
                    },
                ];
            },
        );

    return {
        kind: 'FINAL',

        gameId: response.gameId,
        status: response.status,
        displayMode: response.displayMode,

        homeTeam,
        awayTeam,

        ...toGameDateViewModel(
            response.startTime,
        ),

        venue:
            response.displayMode === 'REVEALED'
                ? response.venue?.trim()
                    || null
                : null,

        headline:
            response.headline?.trim()
            || null,

        homeScore,
        awayScore,

        inningScores:
            isRevealed
                ? toInningScoresViewModel(
                    response.inningScores,
                    awayTeam.abbr,
                    homeTeam.abbr,
                    awayScore,
                    homeScore,
                )
                : null,

        tensionPoints,

        /*
         * 득점 플레이는 종료 공개 응답에서만 존재한다.
         */
        scoringPlays:
            isRevealed
                ? response.scoringSummary
                    .flatMap(
                        (play, index) => {
                            const text =
                                play.text.trim();

                            if (
                                play.inning === null
                                || text.length === 0
                            ) {
                                return [];
                            }

                            const inningType =
                                normalizeInningType(
                                    play.inningType,
                                );

                            return [
                                {
                                    /*
                                     * 현재 API 응답에는 별도 play ID가 없으므로
                                     * 화면 목록 key용으로 응답 순서를 사용한다.
                                     */
                                    eventId:
                                        index + 1,

                                    inning:
                                    play.inning,

                                    ...(inningType
                                        ? {
                                            inningType,
                                        }
                                        : {}),

                                    text,
                                },
                            ];
                        },
                    )
                : [],
    };
}

/**
 * 경기 상태에 따라 적절한 화면 모델 변환기를 선택한다.
 */
export function toGameDetailViewModel(
    response: GameDetailResponse,
): GameDetailViewModel {
    if (
        response.status
        === 'STATUS_SCHEDULED'
    ) {
        return toScheduledGameDetailViewModel(
            response,
        );
    }

    if (
        response.status
        === 'STATUS_FINAL'
    ) {
        return toFinalGameDetailViewModel(
            response,
        );
    }

    return toLiveGameDetailViewModel(
        response,
    );
}