import type { BoxScoreLine } from '../../../shared/components/BoxScoreTable';
import type { TimelineEvent } from '../components/EventTimeline';
import type { TensionPoint } from '../components/TensionCurve';

export interface FinalFixtureTeam {
    name: string;
    abbr: string;

    /**
     * 팀 심볼 이미지 주소
     * 주소가 없거나 이미지 로딩에 실패하면 약어를 표시한다.
     */
    logoUrl?: string | null;
}

export interface FinalFixtureInningScores {
    awayLine: BoxScoreLine;
    homeLine: BoxScoreLine;
}

export interface FinalGameDetailFixture {
    gameId: number;
    season: number;
    dateLabel: string;
    venue: string;

    awayTeam: FinalFixtureTeam;
    homeTeam: FinalFixtureTeam;

    awayScore: number;
    homeScore: number;

    /**
     * 결과와 승리 팀을 드러내지 않는 보호 모드용 문구다.
     */
    protectedHeadline: string | null;

    /**
     * 최종 점수와 경기 결과를 반영할 수 있는 공개 모드용 문구다.
     */
    revealedHeadline: string | null;

    /**
     * 종료 경기 공개 모드의 이닝별 점수 데이터다.
     *
     * 데이터가 없으면 null이며,
     * 화면에서는 점수판 카드 전체를 렌더링하지 않는다.
     */
    inningScores: FinalFixtureInningScores | null;

    /**
     * 종료 경기에서만 사용하는 경기 흐름 데이터다.
     * level은 1에서 5 사이의 긴장도 단계다.
     */
    tensionPoints: TensionPoint[];

    /**
     * 보호·공개 모드 공통 이벤트다.
     *
     * 보호 모드에서는 EventTimeline이 초·말을 숨기고
     * 이닝 숫자만 기준으로 그룹화한다.
     */
    events: TimelineEvent[];

    /**
     * 득점 결과를 포함하는 공개 모드 전용 플레이 목록이다.
     */
    scoringPlays: TimelineEvent[];
}

export const finalGameDetailFixture = {
    gameId: 900003,
    season: 2026,
    dateLabel: '7/9',
    venue: 'Oracle Park',

    awayTeam: {
        name: 'Los Angeles Dodgers',
        abbr: 'LAD',
        logoUrl: null,
    },

    homeTeam: {
        name: 'San Francisco Giants',
        abbr: 'SF',
        logoUrl: null,
    },

    awayScore: 4,
    homeScore: 6,

    protectedHeadline:
        '후반까지 긴장감이 이어지며 여러 차례 흐름이 움직인 경기였습니다.',

    revealedHeadline:
        '8회말 2득점으로 San Francisco Giants가 6-4 승리를 완성했습니다.',

    /**
     * 이닝별 점수는 공개 모드에서만 표시한다.
     */
    inningScores: {
        awayLine: {
            abbr: 'LAD',
            innings: [0, 0, 0, 1, 0, 0, 2, 0, 0],
            runs: 3,
        },

        homeLine: {
            abbr: 'SF',
            /**
             * 홈팀이 마지막 공격을 하지 않은 경우 null을 사용하며
             * 화면에서는 '-'로 표시한다.
             */
            innings: [0, 1, 0, 0, 0, 1, 0, 3, null],
            runs: 5,
        },
    },

    /**
     * 그래프는 실제 경기 흐름을 1~5 범위로 표현한다.
     * 진행 경기에는 표시하지 않고 종료 경기에서만 사용한다.
     */
    tensionPoints: [
        {
            inning: 1,
            inningType: 'BOTTOM',
            level: 1,
        },
        {
            inning: 2,
            inningType: 'BOTTOM',
            level: 2,
        },
        {
            inning: 3,
            inningType: 'BOTTOM',
            level: 2,
        },
        {
            inning: 4,
            inningType: 'BOTTOM',
            level: 4,
        },
        {
            inning: 5,
            inningType: 'BOTTOM',
            level: 3,
        },
        {
            inning: 6,
            inningType: 'BOTTOM',
            level: 3,
        },
        {
            inning: 7,
            inningType: 'BOTTOM',
            level: 5,
        },
        {
            inning: 8,
            inningType: 'BOTTOM',
            level: 5,
        },
        {
            inning: 9,
            inningType: 'TOP',
            level: 4,
        },
    ],

    /**
     * 보호 모드에서도 노출할 수 있는 안전한 이벤트 문구다.
     * 점수, 우세 팀, 득점 선수와 같은 결과 정보는 넣지 않는다.
     */
    events: [
        {
            eventId: 301,
            inning: 2,
            inningType: 'TOP',
            text: '득점권 기회가 만들어지며 경기의 긴장감이 높아졌습니다.',
        },
        {
            eventId: 302,
            inning: 4,
            inningType: 'BOTTOM',
            text: '연속 출루로 흐름이 크게 움직였습니다.',
            highlighted: true,
        },
        {
            eventId: 303,
            inning: 5,
            inningType: 'TOP',
            text: '강한 타구가 이어지며 다시 긴장감이 높아졌습니다.',
        },
        {
            eventId: 304,
            inning: 7,
            inningType: 'BOTTOM',
            text: '경기 후반 중요한 득점권 상황이 이어졌습니다.',
            highlighted: true,
        },
        {
            eventId: 305,
            inning: 8,
            inningType: 'BOTTOM',
            text: '경기 막판 흐름을 바꿀 수 있는 장면이 나왔습니다.',
        },
        {
            eventId: 306,
            inning: 9,
            inningType: 'TOP',
            text: '마지막 이닝까지 높은 긴장감이 유지됐습니다.',
        },
    ],

    /**
     * 점수와 타석 결과가 포함되므로 공개 모드에서만 표시한다.
     */
    scoringPlays: [
        {
            eventId: 401,
            inning: 2,
            inningType: 'TOP',
            text: '희생플라이로 원정팀이 1점을 기록했습니다.',
        },
        {
            eventId: 402,
            inning: 3,
            inningType: 'BOTTOM',
            text: '적시타로 홈팀이 1점을 기록했습니다.',
        },
        {
            eventId: 403,
            inning: 4,
            inningType: 'BOTTOM',
            text: '2타점 2루타로 홈팀이 2점을 추가했습니다.',
            highlighted: true,
        },
        {
            eventId: 404,
            inning: 5,
            inningType: 'TOP',
            text: '2점 홈런으로 원정팀이 2점을 추가했습니다.',
            highlighted: true,
        },
        {
            eventId: 405,
            inning: 7,
            inningType: 'TOP',
            text: '적시타로 원정팀이 1점을 기록했습니다.',
        },
        {
            eventId: 406,
            inning: 7,
            inningType: 'BOTTOM',
            text: '희생플라이로 홈팀이 1점을 추가했습니다.',
        },
        {
            eventId: 407,
            inning: 8,
            inningType: 'BOTTOM',
            text: '2타점 적시타로 홈팀이 2점을 추가했습니다.',
            highlighted: true,
        },
    ],
} satisfies FinalGameDetailFixture;
