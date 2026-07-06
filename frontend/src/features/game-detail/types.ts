/**
 * 경기 상세 API의 표시 모드다.
 *
 * protected:
 * - 기본값
 * - 점수, 팀명, play 원문 같은 스포일러 정보를 숨긴다.
 *
 * revealed:
 * - 사용자가 공개 전환을 선택했을 때 사용한다.
 * - 점수, 팀명, play 원문을 보여준다.
 */
export type DisplayMode = 'PROTECTED' | 'REVEALED' | 'NORMAL'

/**
 * 상세 화면에서 사용할 mode query 값이다.
 *
 * 백엔드 enum은 대문자지만,
 * API 요청 query parameter는 기존 검증 흐름에 맞춰 소문자를 사용한다.
 */
export type GameDetailRequestMode = 'protected' | 'revealed'

/**
 * protected/revealed 모드에서 공통으로 사용하는 경기 흐름 블록이다.
 *
 * 이 블록은 protected 모드에서도 표시되므로
 * 점수, 팀명, play 원문, 타석 결과 같은 스포일러 필드를 포함하지 않는다.
 */
export interface LiveUpdateBlock {
    timeLabel: string
    periodLabel: string
    title: string
    description: string
    tags: string[]
    intensity: 'LOW' | 'MEDIUM' | 'HIGH'
}

/**
 * protected 모드에서 내려오는 추천 요약이다.
 *
 * 내부 watchScore 숫자는 화면에 직접 보여주지 않고,
 * 사용자가 이해할 수 있는 spoiler-safe 태그만 표시한다.
 */
export interface ProtectedSummary {
    reasonTags: string[]
}

/**
 * protected 모드의 최근 play 정보다.
 *
 * play text, 점수, 득점 여부는 스포일러가 될 수 있으므로 포함하지 않는다.
 */
export interface ProtectedPlay {
    type: string
    inning: number | null
    inningType: string | null
    outs: number | null
    balls: number | null
    strikes: number | null
}

/**
 * protected 모드 경기 상세 응답이다.
 *
 * 팀명과 점수는 포함하지 않는다.
 * 화면에서는 periodLabel, summary, recentPlays, liveUpdateBlocks를 중심으로 표시한다.
 */
export interface ProtectedGameDetailResponse {
    gameId: number
    status: string
    startTime: string
    periodLabel: string
    summary: ProtectedSummary
    recentPlays: ProtectedPlay[]
    liveUpdateBlocks: LiveUpdateBlock[]
    displayMode: 'PROTECTED'
}

/**
 * 공개 모드에서 표시할 팀 정보다.
 *
 * protected 모드에서는 팀 정보를 사용하지 않는다.
 */
export interface Team {
    id: number | null
    name: string | null
    abbr: string | null
}

/**
 * 공개 모드에서만 표시하는 실제 경기 점수다.
 */
export interface Score {
    home: number | null
    away: number | null
}

/**
 * 공개 모드에서만 내려오는 추천 점수 요약이다.
 *
 * 현재 백엔드는 revealed 모드에서 scoreSummary를 내려주지만,
 * 최신 정책상 내부 추천 점수는 화면에 직접 노출하지 않는다.
 * 따라서 프론트에서는 필요할 때 디버깅 용도로만 보고, 사용자 화면에는 표시하지 않는다.
 */
export interface ScoreSummary {
    baseScore: number
    watchScore: number
    signals: Record<string, number>
    reasonTags: string[]
    configVersion: number
    calculatedAt: string
}

/**
 * 공개 모드의 최근 play 정보다.
 *
 * 사용자가 공개 전환을 선택한 뒤에만 play text, 점수 변화, 득점 여부를 표시한다.
 */
export interface RevealedPlay {
    id: number
    playOrder: number
    type: string
    inning: number | null
    inningType: string | null
    text: string | null
    homeScore: number | null
    awayScore: number | null
    scoringPlay: boolean | null
    scoreValue: number | null
    outs: number | null
    balls: number | null
    strikes: number | null
    fetchedAt: string | null
}

/**
 * revealed 모드 경기 상세 응답이다.
 *
 * 공개 모드에서는 팀명, 점수, play 원문을 표시할 수 있다.
 * 단, 내부 추천 점수는 최신 정책상 사용자 화면에 직접 노출하지 않는다.
 */
export interface RevealedGameDetailResponse {
    gameId: number
    status: string
    startTime: string
    period: number | null
    homeTeam: Team
    awayTeam: Team
    score: Score
    scoreSummary: ScoreSummary | null
    recentPlays: RevealedPlay[]
    liveUpdateBlocks: LiveUpdateBlock[]
    displayMode: 'REVEALED'
}

/**
 * 경기 상세 API는 mode에 따라 protected 또는 revealed 응답을 반환한다.
 *
 * displayMode 값으로 타입을 구분해서 화면에서 안전하게 분기한다.
 */
export type GameDetailResponse =
    | ProtectedGameDetailResponse
    | RevealedGameDetailResponse