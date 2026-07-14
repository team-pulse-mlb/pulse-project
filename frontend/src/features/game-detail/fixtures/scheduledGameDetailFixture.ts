export interface ScheduledFixtureTeam {
    name: string;
    abbr: string;
    logoUrl?: string | null;
}

export interface ScheduledGameDetailFixture {
    gameId: number;
    season: number;
    dateLabel: string;
    badgeLabel: string;
    venue: string;
    venueDetail: string;
    startTimeLabel: string;
    awayTeam: ScheduledFixtureTeam;
    homeTeam: ScheduledFixtureTeam;
    probablePitchers: {
        away: string;
        home: string;
    };
    favoritePlayerNotice: string;
}

/**
 * 예정 경기 상세 UI 확인을 위한 개발용 fixture다.
 * 실제 API 연결 단계에서는 서버의 예정 경기 응답으로 교체한다.
 */
export const scheduledGameDetailFixture = {
    gameId: 900002,
    season: 2026,
    dateLabel: '7/10',
    badgeLabel: '예정 · 7/10 08:05',
    venue: 'Yankee Stadium',
    venueDetail: 'Yankee Stadium, New York',
    startTimeLabel: '7/10 (목) 08:05 KST',

    awayTeam: {
        name: 'Boston Red Sox',
        abbr: 'BOS',
        logoUrl: null,
    },

    homeTeam: {
        name: 'New York Yankees',
        abbr: 'NYY',
        logoUrl: null,
    },

    probablePitchers: {
        away: '미확정',
        home: '미확정',
    },

    favoritePlayerNotice:
        '관심 선수를 등록하면 선발 라인업이 확정될 때 출전 여부를 알려드려요.',
} satisfies ScheduledGameDetailFixture;