import type { BoxScoreLine } from '../../../shared/components/BoxScoreTable';
import type { Situation } from '../components/CurrentSituationCard';
import type { TensionPoint } from '../components/TensionCurve';

export interface FixtureTeam {
    /** 히어로 영역에서 크게 표시할 팀 전체 이름 */
    name: string;

    /** 팀명 아래와 로고 폴백에 사용하는 팀 약어 */
    abbr: string;

    /**
     * API에서 전달받을 팀 심볼 이미지 주소다.
     * 값이 없거나 이미지 로딩에 실패하면 약어를 대신 표시한다.
     */
    logoUrl?: string | null;
}

export interface FixtureTimelineEvent {
    eventId: number;
    inning: number;
    inningType: 'TOP' | 'BOTTOM';
    text: string;
    highlighted?: boolean;
}

export interface FixtureCurrentPlayer {
    id: number;
    name: string;
}

export interface FixtureCurrentMatchup {
    pitcher: FixtureCurrentPlayer;
    batter: FixtureCurrentPlayer;
}

export interface FixtureRecentPlay {
    playId: number;
    inning: number;
    inningType: 'TOP' | 'BOTTOM';
    text: string;
    scoreLabel: string;
    highlighted?: boolean;
}

export interface FixtureInningScores {
    awayLine: BoxScoreLine;
    homeLine: BoxScoreLine;
}

export interface LiveGameDetailFixture {
    gameId: number;
    season: number;
    dateLabel: string;
    venue: string;

    inning: number;
    inningType: 'TOP' | 'BOTTOM';

    awayTeam: FixtureTeam;
    homeTeam: FixtureTeam;

    awayScore: number;
    homeScore: number;

    situation: Situation;

    /**
     * 현재 타자·투수 이름은 공개 모드에서만 전달하고 표시한다.
     * 실제 API 연결 시 공개 응답의 현재 타석 데이터로 교체한다.
     */
    currentMatchup: FixtureCurrentMatchup;

    /**
     * 공개 모드의 이닝별 점수 데이터다.
     *
     * API가 이 데이터를 제공하지 않으면 null로 두며,
     * 화면에서는 이닝별 점수판 카드 전체를 렌더링하지 않는다.
     */
    inningScores: FixtureInningScores | null;

    /**
     * 종료 경기 그래프 컴포넌트의 데이터 구조를 확인하기 위한 값이다.
     * 진행 경기 상세에는 긴장도 곡선을 표시하지 않는다.
     */
    tensionPoints: TensionPoint[];

    /**
     * 보호 모드에서도 사용할 수 있는 안전한 이벤트 문구다.
     * 실제 연동 시 서버가 필터링한 이벤트 응답으로 교체한다.
     */
    events: FixtureTimelineEvent[];

    /**
     * 점수, 초·말, 타석 결과를 포함하므로
     * 공개 모드에서만 화면에 표시한다.
     */
    recentPlays: FixtureRecentPlay[];
}

/**
 * 진행 중 경기 상세 UI 확인을 위한 개발용 fixture다.
 *
 * 실제 API 연결 브랜치에서는 서버 응답으로 교체하며,
 * 컴포넌트의 화면 배치와 보호·공개 분기는 유지한다.
 */
export const liveGameDetailFixture = {
    gameId: 900001,
    season: 2026,
    dateLabel: '7/10',
    venue: 'Wrigley Field',

    inning: 8,
    inningType: 'TOP',

    awayTeam: {
        name: 'San Diego Padres',
        abbr: 'SD',
        logoUrl: null,
    },

    homeTeam: {
        name: 'Chicago Cubs',
        abbr: 'CHC',
        logoUrl: null,
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
     * 공개 모드에서만 현재 타석 카드에 표시한다.
     * API 연결 후에는 실제 선수 이름으로 교체된다.
     */
    currentMatchup: {
        pitcher: {
            id: 2001,
            name: 'Shota Imanaga',
        },

        batter: {
            id: 1001,
            name: 'Fernando Tatis Jr.',
        },
    },

    /**
     * 공개 모드에서만 이닝별 점수판에 전달한다.
     *
     * 배열에 없는 미래 이닝과 null 값은 화면에서 '-'로 표시된다.
     * 10회 이상 데이터가 추가되면 점수판 헤더도 자동 확장된다.
     */
    inningScores: {
        awayLine: {
            abbr: 'SD',
            innings: [0, 1, 0, 2, 0, 0, 0, 1],
            runs: 4,
            hits: 8,
            errors: 1,
        },

        homeLine: {
            abbr: 'CHC',
            innings: [0, 0, 1, 0, 2, 0, 0, null],
            runs: 3,
            hits: 7,
            errors: 0,
        },
    },

    /**
     * 긴장도 그래프 데이터 구조 확인용 예시다.
     * 진행 중 상세 화면에서는 사용하지 않는다.
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

    /**
     * 최근 플레이는 공개 모드에서만 렌더링한다.
     */
    recentPlays: [
        {
            playId: 1,
            inning: 8,
            inningType: 'TOP',
            text: '스트라이크아웃 (2아웃)',
            scoreLabel: 'SD 4 - 3 CHC',
        },
        {
            playId: 2,
            inning: 8,
            inningType: 'TOP',
            text: 'Kim 2루타, 1점 득점',
            scoreLabel: 'SD 4 - 3 CHC',
            highlighted: true,
        },
        {
            playId: 3,
            inning: 7,
            inningType: 'BOTTOM',
            text: '병살타로 위기 탈출',
            scoreLabel: 'SD 3 - 3 CHC',
        },
    ],
} satisfies LiveGameDetailFixture;