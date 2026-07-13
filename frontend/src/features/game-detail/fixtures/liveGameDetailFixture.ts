import type { Situation } from '../components/CurrentSituationCard';
import type { TensionPoint } from '../components/TensionCurve';

export interface FixtureTeam {
    abbr: string;
    name: string;
}

export interface FixtureTimelineEvent {
    eventId: number;
    inning: number;
    inningType: 'TOP' | 'BOTTOM';
    text: string;
    highlighted?: boolean;
}

export interface LiveGameDetailFixture {
    gameId: number;
    season: number;
    venue: string;
    inning: number;
    inningType: 'TOP' | 'BOTTOM';
    awayTeam: FixtureTeam;
    homeTeam: FixtureTeam;
    awayScore: number;
    homeScore: number;
    situation: Situation;
    tensionPoints: TensionPoint[];
    events: FixtureTimelineEvent[];
}

/**
 * 경기 상세 UI를 먼저 확인을 위한 fixture
 */
export const liveGameDetailFixture = {
    gameId: 900001,
    season: 2026,
    venue: 'Wrigley Field',
    inning: 8,
    inningType: 'TOP',

    awayTeam: {
        abbr: 'SD',
        name: 'San Diego',
    },

    homeTeam: {
        abbr: 'CHC',
        name: 'Chicago Cubs',
    },

    awayScore: 4,
    homeScore: 3,

    situation: {
        balls: 3,
        strikes: 2,
        outs: 2,
        runnerOnFirst: false,
        runnerOnSecond: true,
        runnerOnThird: false,
        scoringPosition: true,
        basesLoaded: false,
    },

    /**
     * 개발 화면 확인을 위한 서버 응답 형태의 예시 값이다.
     * 그래프 컴포넌트는 이 값을 수정하지 않고 그대로 표현한다.
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
            level: 3,
        },
        {
            inning: 5,
            inningType: 'BOTTOM',
            level: 3,
        },
        {
            inning: 6,
            inningType: 'BOTTOM',
            level: 4,
        },
        {
            inning: 7,
            inningType: 'BOTTOM',
            level: 5,
        },
        {
            inning: 8,
            inningType: 'TOP',
            level: 4,
        },
    ],


    events: [
        {
            eventId: 1,
            inning: 3,
            inningType: 'TOP',
            text: '연속 출루로 득점권 기회를 만들었습니다.',
        },
        {
            eventId: 2,
            inning: 5,
            inningType: 'BOTTOM',
            text: '긴 승부가 이어지며 흐름이 달아올랐습니다.',
        },
        {
            eventId: 3,
            inning: 7,
            inningType: 'BOTTOM',
            text: '만루 위기를 막아냈습니다.',
        },
        {
            eventId: 4,
            inning: 8,
            inningType: 'TOP',
            text: '강한 타구가 이어졌습니다.',
            highlighted: true,
        },
    ],
} satisfies LiveGameDetailFixture;