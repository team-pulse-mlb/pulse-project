import type { BoxScoreLine } from '../../../shared/components/BoxScoreTable';
import type { TensionPoint } from '../components/TensionCurve';

interface FinalFixtureTeam {
    abbr: string;
    name: string;
}

interface FinalTimelineEventFixture {
    eventId: number;
    inning: number;
    inningType: 'TOP' | 'BOTTOM';
    protectedText: string;
    revealedText: string;
    highlighted?: boolean;
}

interface FinalScoringPlayFixture {
    eventId: number;
    inning: number;
    inningType: 'TOP' | 'BOTTOM';
    text: string;
    scoreLabel: string;
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
    headline: {
        protected: string | null;
        revealed: string | null;
    };
    protectedTensionPoints: TensionPoint[];
    revealedTensionPoints: TensionPoint[];
    events: FinalTimelineEventFixture[];
    awayLine: BoxScoreLine;
    homeLine: BoxScoreLine;
    scoringPlays: FinalScoringPlayFixture[];
}

/**
 * 종료 경기 상세 UI를 먼저 확인하기 위한 개발용 fixture다.
 * 보호·공개 모드의 데이터 차이를 실제 API 응답과 유사하게 분리한다.
 */
export const finalGameDetailFixture = {
    gameId: 900003,
    season: 2026,
    dateLabel: '7/9',
    venue: 'Dodger Stadium',

    awayTeam: {
        abbr: 'SD',
        name: 'San Diego',
    },

    homeTeam: {
        abbr: 'LAD',
        name: 'Los Angeles',
    },

    awayScore: 4,
    homeScore: 6,

    headline: {
        protected:
            '후반까지 긴장감이 이어지며 여러 차례 중요한 승부가 등장한 경기입니다.',
        revealed:
            '다저스가 7회 흐름을 바꾸며 6대 4로 승리한 경기입니다.',
    },

    /**
     * 종료 보호 모드는 공격 방향을 숨긴 이닝 단위 레벨만 사용한다.
     */
    protectedTensionPoints: [
        { inning: 1, level: 1 },
        { inning: 2, level: 2 },
        { inning: 3, level: 2 },
        { inning: 4, level: 3 },
        { inning: 5, level: 2 },
        { inning: 6, level: 4 },
        { inning: 7, level: 5 },
        { inning: 8, level: 4 },
        { inning: 9, level: 3 },
    ],

    /**
     * 종료 공개 모드는 초·말을 포함한 하프이닝 단위 레벨을 사용한다.
     * 그래프 컴포넌트는 서버 값을 재계산하지 않고 그대로 표현한다.
     */
    revealedTensionPoints: [
        { inning: 1, inningType: 'TOP', level: 1 },
        { inning: 1, inningType: 'BOTTOM', level: 1 },
        { inning: 2, inningType: 'TOP', level: 2 },
        { inning: 2, inningType: 'BOTTOM', level: 2 },
        { inning: 3, inningType: 'TOP', level: 2 },
        { inning: 3, inningType: 'BOTTOM', level: 3 },
        { inning: 4, inningType: 'TOP', level: 3 },
        { inning: 4, inningType: 'BOTTOM', level: 2 },
        { inning: 5, inningType: 'TOP', level: 2 },
        { inning: 5, inningType: 'BOTTOM', level: 3 },
        { inning: 6, inningType: 'TOP', level: 4 },
        { inning: 6, inningType: 'BOTTOM', level: 4 },
        { inning: 7, inningType: 'TOP', level: 4 },
        { inning: 7, inningType: 'BOTTOM', level: 5 },
        { inning: 8, inningType: 'TOP', level: 4 },
        { inning: 8, inningType: 'BOTTOM', level: 3 },
        { inning: 9, inningType: 'TOP', level: 3 },
        { inning: 9, inningType: 'BOTTOM', level: 3 },
    ],

    events: [
        {
            eventId: 301,
            inning: 3,
            inningType: 'BOTTOM',
            protectedText: '득점권에서 긴 승부가 이어졌습니다.',
            revealedText: '3회 말 다저스가 적시타로 먼저 앞서갔습니다.',
        },
        {
            eventId: 302,
            inning: 6,
            inningType: 'TOP',
            protectedText: '연속 출루로 압박이 크게 높아졌습니다.',
            revealedText: '6회 초 샌디에이고가 연속 안타로 동점을 만들었습니다.',
            highlighted: true,
        },
        {
            eventId: 303,
            inning: 7,
            inningType: 'BOTTOM',
            protectedText: '후반 중요한 흐름 변화가 나타났습니다.',
            revealedText: '7회 말 다저스가 2점을 추가하며 다시 앞서갔습니다.',
        },
        {
            eventId: 304,
            inning: 9,
            inningType: 'TOP',
            protectedText: '마지막까지 주자가 출루하며 긴장감이 이어졌습니다.',
            revealedText: '9회 초 샌디에이고가 주자를 내보냈지만 득점하지 못했습니다.',
        },
    ],

    awayLine: {
        abbr: 'SD',
        innings: [0, 0, 1, 0, 0, 3, 0, 0, 0],
        runs: 4,
        hits: 8,
        errors: 1,
    },

    homeLine: {
        abbr: 'LAD',
        innings: [0, 0, 2, 0, 0, 2, 2, 0, null],
        runs: 6,
        hits: 10,
        errors: 0,
    },

    scoringPlays: [
        {
            eventId: 401,
            inning: 3,
            inningType: 'BOTTOM',
            text: '중전 적시타로 두 명의 주자가 홈을 밟았습니다.',
            scoreLabel: 'SD 1 : 2 LAD',
        },
        {
            eventId: 402,
            inning: 6,
            inningType: 'TOP',
            text: '연속 안타와 희생플라이로 경기가 다시 원점이 됐습니다.',
            scoreLabel: 'SD 4 : 4 LAD',
        },
        {
            eventId: 403,
            inning: 7,
            inningType: 'BOTTOM',
            text: '2타점 2루타로 결승점을 만들었습니다.',
            scoreLabel: 'SD 4 : 6 LAD',
        },
    ],
} satisfies FinalGameDetailFixture;