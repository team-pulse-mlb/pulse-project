import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { fetchGameDetail } from '../api/gameDetailApi'
import type {
    BaseState,
    GameDetailRequestMode,
    GameDetailResponse,
    LineScoreInning,
    LiveUpdateBlock,
    ProtectedPlay,
    RevealedPlay,
    Score,
    Team,
} from '../types'
import './GameDetailPage.css'

/**
 * 경기 상세 페이지다.
 *
 * 이번 수정 기준:
 * - 공개 모드: 점수, 이닝별 득점판, 볼카운트, 베이스, 최근 플레이 원문 표시
 * - 보호 모드: 팀 정보와 이닝 숫자만 표시하고, 점수/득점판/베이스/플레이 원문은 숨김
 * - 우측 경기 변동 알림은 추천 점수 등급 없이 최신순으로 누적 표시
 */
export function GameDetailPage() {
    const { id } = useParams<{ id: string }>()
    const [mode, setMode] = useState<GameDetailRequestMode>('protected')
    const [gameDetail, setGameDetail] = useState<GameDetailResponse | null>(null)
    const [isLoading, setIsLoading] = useState(true)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)

    useEffect(() => {
        if (!id) {
            setErrorMessage('경기 ID가 없습니다.')
            setIsLoading(false)
            return
        }

        /**
         * useParams의 id는 undefined일 수 있으므로 검증 후 별도 변수에 담아 사용한다.
         */
        const gameId = id

        async function loadGameDetail() {
            setIsLoading(true)
            setErrorMessage(null)

            try {
                /**
                 * mode 값에 따라 protected/revealed 상세 응답을 받아온다.
                 */
                const data = await fetchGameDetail(gameId, mode)
                setGameDetail(data)
            } catch (error) {
                const message =
                    error instanceof Error ? error.message : '경기 상세 정보를 불러오지 못했습니다.'
                setErrorMessage(message)
            } finally {
                setIsLoading(false)
            }
        }

        loadGameDetail()
    }, [id, mode])

    const statusLabel = useMemo(() => getStatusLabel(gameDetail?.status), [gameDetail?.status])

    const venueLabel = gameDetail?.venueName ?? '구장 정보 확인 중'

    const inningLabel = useMemo(() => {
        if (!gameDetail) {
            return '이닝 확인 중'
        }

        /**
         * 보호 모드에서는 초/말을 노출하지 않고 이닝만 보여준다.
         */
        if (gameDetail.displayMode === 'PROTECTED') {
            return getProtectedInningLabel(gameDetail.recentPlays, gameDetail.periodLabel)
        }

        /**
         * 공개 모드에서는 초/말까지 보여준다.
         */
        return getRevealedInningLabel(gameDetail.period, gameDetail.recentPlays[0])
    }, [gameDetail])

    function handleModeChange(nextMode: GameDetailRequestMode) {
        if (nextMode === mode) {
            return
        }

        /**
         * 공개 모드는 점수와 플레이 원문이 노출될 수 있으므로 사용자 확인을 받는다.
         */
        if (nextMode === 'revealed') {
            const confirmed = window.confirm(
                '공개 모드로 전환하면 점수와 경기 내용이 표시됩니다. 계속할까요?',
            )

            if (!confirmed) {
                return
            }
        }

        setMode(nextMode)
    }

    if (isLoading && gameDetail === null) {
        return (
            <main className="game-detail-page">
                <p className="game-detail-message">경기 상세 정보를 불러오는 중입니다...</p>
            </main>
        )
    }

    if (errorMessage) {
        return (
            <main className="game-detail-page">
                <p className="game-detail-message">{errorMessage}</p>
            </main>
        )
    }

    if (!gameDetail) {
        return (
            <main className="game-detail-page">
                <p className="game-detail-message">표시할 경기 정보가 없습니다.</p>
            </main>
        )
    }

    return (
        <main className="game-detail-page">
            <div className="game-dashboard">
                <section className="dashboard-main">
                    <header className="dashboard-topbar">
                        <div className="game-state-area">
                            <StatusBadge label={statusLabel} />
                            <span className="venue-inning-text">
                {venueLabel} | {inningLabel}
              </span>
                        </div>

                        <ModeToggle mode={mode} onChange={handleModeChange} />
                    </header>

                    <section className="dashboard-card">
                        <TeamScoreStrip gameDetail={gameDetail} />

                        {gameDetail.displayMode === 'REVEALED' && (
                            <div className="middle-grid">
                                <LineScorePanel
                                    awayTeam={gameDetail.awayTeam}
                                    homeTeam={gameDetail.homeTeam}
                                    score={gameDetail.score}
                                    lineScores={gameDetail.lineScores ?? []}
                                />

                                <CountAndBasePanel
                                    latestPlay={gameDetail.recentPlays[0]}
                                    bases={gameDetail.bases ?? null}
                                />
                            </div>
                        )}

                        {gameDetail.displayMode === 'PROTECTED' ? (
                            <ProtectedDataArea gameDetail={gameDetail} />
                        ) : (
                            <RevealedDataArea gameDetail={gameDetail} />
                        )}
                    </section>
                </section>

                <aside className="update-panel">
                    <UpdatePanelHeader />
                    <LiveUpdateList blocks={gameDetail.liveUpdateBlocks} />
                </aside>
            </div>
        </main>
    )
}

function getStatusLabel(status?: string): 'LIVE' | '경기 전' | '경기 종료' {
    if (!status) {
        return '경기 전'
    }

    const normalizedStatus = status.toUpperCase()

    if (
        normalizedStatus.includes('LIVE') ||
        normalizedStatus.includes('IN_PROGRESS') ||
        normalizedStatus.includes('PROGRESS')
    ) {
        return 'LIVE'
    }

    if (
        normalizedStatus.includes('FINAL') ||
        normalizedStatus.includes('COMPLETE') ||
        normalizedStatus.includes('ENDED')
    ) {
        return '경기 종료'
    }

    return '경기 전'
}

/**
 * 보호 모드에서는 이닝만 출력한다.
 */
function getProtectedInningLabel(plays: ProtectedPlay[], periodLabel: string): string {
    const latestInning = plays.find((play) => play.inning !== null)?.inning

    if (latestInning !== undefined && latestInning !== null) {
        return `${latestInning}회`
    }

    const inningNumber = periodLabel.match(/\d+/)?.[0]
    return inningNumber ? `${inningNumber}회` : periodLabel
}

/**
 * 공개 모드에서는 초/말까지 출력한다.
 */
function getRevealedInningLabel(
    period: number | null,
    latestPlay: RevealedPlay | undefined,
): string {
    const inning = latestPlay?.inning ?? period

    if (inning === null || inning === undefined) {
        return '이닝 확인 중'
    }

    const halfLabel = getHalfInningLabel(latestPlay?.inningType)
    return halfLabel ? `${inning}회 ${halfLabel}` : `${inning}회`
}

function getHalfInningLabel(inningType?: string | null): string {
    if (!inningType) {
        return ''
    }

    const normalized = inningType.toUpperCase()

    if (
        normalized === 'TOP' ||
        normalized === 'T' ||
        normalized.includes('TOP') ||
        normalized.includes('초')
    ) {
        return '초'
    }

    if (
        normalized === 'BOTTOM' ||
        normalized === 'BOT' ||
        normalized === 'B' ||
        normalized.includes('BOTTOM') ||
        normalized.includes('말')
    ) {
        return '말'
    }

    return ''
}

function StatusBadge({ label }: { label: 'LIVE' | '경기 전' | '경기 종료' }) {
    return (
        <span className={`status-badge ${label === 'LIVE' ? 'is-live' : ''}`}>
            <span className="status-dot" />
            {label}
    </span>
    )
}

function ModeToggle({
                        mode,
                        onChange,
                    }: {
    mode: GameDetailRequestMode
    onChange: (mode: GameDetailRequestMode) => void
}) {
    return (
        <div className="mode-toggle" aria-label="모드 변경">
            <button
                type="button"
                className={`mode-toggle-button ${mode === 'protected' ? 'is-active' : ''}`}
                onClick={() => onChange('protected')}
            >
                보호
            </button>
            <button
                type="button"
                className={`mode-toggle-button ${mode === 'revealed' ? 'is-active' : ''}`}
                onClick={() => onChange('revealed')}
            >
                공개
            </button>
        </div>
    )
}

/**
 * 팀 심볼, 팀명, 팀 약어, 점수를 한 줄에 배치한다.
 * 왼쪽 팀: 심볼 → 팀명 → 점수
 * 오른쪽 팀: 점수 → 팀명 → 심볼
 */
function TeamScoreStrip({ gameDetail }: { gameDetail: GameDetailResponse }) {
    const isRevealed = gameDetail.displayMode === 'REVEALED'

    return (
        <section className="team-score-strip">
            <TeamSide
                team={gameDetail.awayTeam}
                side="away"
                score={isRevealed ? gameDetail.score.away : null}
                isProtected={!isRevealed}
            />

            <span className="vs-label">VS</span>

            <TeamSide
                team={gameDetail.homeTeam}
                side="home"
                score={isRevealed ? gameDetail.score.home : null}
                isProtected={!isRevealed}
            />
        </section>
    )
}

function TeamSide({
                      team,
                      side,
                      score,
                      isProtected,
                  }: {
    team: Team
    side: 'away' | 'home'
    score: number | null
    isProtected: boolean
}) {
    return (
        <article className={`team-side team-side-${side} ${isProtected ? 'is-protected' : ''}`}>
            {side === 'away' ? (
                <>
                    <div className="team-identity-group">
                        <TeamSymbol team={team} />
                        <TeamName team={team} />
                    </div>

                    {!isProtected && <ScoreBox value={score} isProtected={false} />}
                </>
            ) : (
                <>
                    {!isProtected && <ScoreBox value={score} isProtected={false} />}

                    <div className="team-identity-group team-identity-group-home">
                        <TeamName team={team} />
                        <TeamSymbol team={team} />
                    </div>
                </>
            )}
        </article>
    )
}

/**
 * 팀 심볼 이미지 영역이다.
 * 백엔드에서 logoUrl/symbolUrl/imageUrl이 내려오면 이미지를 출력하고,
 * 값이 없으면 NULL 텍스트를 출력한다.
 */
function TeamSymbol({ team }: { team: Team }) {
    const logoUrl = getTeamSymbolUrl(team)

    return (
        <div className="team-symbol">
            {logoUrl ? (
                <img src={logoUrl} alt={`${team.name ?? team.abbr ?? '팀'} 심볼`} />
            ) : (
                <span>NULL</span>
            )}
        </div>
    )
}

function getTeamSymbolUrl(team: Team): string | null {
    const extendedTeam = team as Team & {
        logoUrl?: string | null
        symbolUrl?: string | null
        imageUrl?: string | null
    }

    return extendedTeam.logoUrl ?? extendedTeam.symbolUrl ?? extendedTeam.imageUrl ?? null
}

function TeamName({ team }: { team: Team }) {
    return (
        <div className="team-copy">
            <strong title={team.name ?? '팀 정보 없음'}>{team.name ?? '팀 정보 없음'}</strong>
            <span>{team.abbr ?? 'TBD'}</span>
        </div>
    )
}

function ScoreBox({
                      value,
                      isProtected,
                  }: {
    value: number | null
    isProtected: boolean
}) {
    return <strong className="score-box">{isProtected ? '🔒' : value ?? '-'}</strong>
}

/**
 * 이닝별 득점판이다.
 * 공개 모드에서만 표시된다.
 */
function LineScorePanel({
                            awayTeam,
                            homeTeam,
                            score,
                            lineScores,
                        }: {
    awayTeam: Team
    homeTeam: Team
    score: Score
    lineScores: LineScoreInning[]
}) {
    const innings = createNineInnings(lineScores)
    const awayAbbr = awayTeam.abbr ?? 'AWY'
    const homeAbbr = homeTeam.abbr ?? 'HOM'

    return (
        <section className="line-score-panel">
            <div className="line-score-table">
                <div className="line-score-row line-score-head">
                    <span />
                    {innings.map((inning) => (
                        <span key={inning.inning}>{inning.inning}</span>
                    ))}
                    <span>R</span>
                    <span>H</span>
                    <span>E</span>
                </div>

                <div className="line-score-row">
                    <strong>{awayAbbr}</strong>
                    {innings.map((inning) => (
                        <span key={`away-${inning.inning}`}>{inning.away ?? '-'}</span>
                    ))}
                    <b>{score.away ?? '-'}</b>
                    <span>-</span>
                    <span>-</span>
                </div>

                <div className="line-score-row">
                    <strong>{homeAbbr}</strong>
                    {innings.map((inning) => (
                        <span key={`home-${inning.inning}`}>{inning.home ?? '-'}</span>
                    ))}
                    <b>{score.home ?? '-'}</b>
                    <span>-</span>
                    <span>-</span>
                </div>
            </div>
        </section>
    )
}

function createNineInnings(lineScores: LineScoreInning[]): LineScoreInning[] {
    return Array.from({ length: 9 }, (_, index) => {
        const inning = index + 1
        const found = lineScores.find((lineScore) => lineScore.inning === inning)

        return {
            inning,
            away: found?.away ?? null,
            home: found?.home ?? null,
        }
    })
}

/**
 * 볼카운트 그래픽과 베이스 그래픽이다.
 * 공개 모드에서만 표시된다.
 */
function CountAndBasePanel({
                               latestPlay,
                               bases,
                           }: {
    latestPlay: RevealedPlay | undefined
    bases: BaseState | null
}) {
    return (
        <section className="count-base-panel">
            <CountGraphic
                balls={latestPlay?.balls ?? 0}
                strikes={latestPlay?.strikes ?? 0}
                outs={latestPlay?.outs ?? 0}
            />
            <BaseGraphic bases={bases} />
        </section>
    )
}

/**
 * B/S/O만 표기하고, 왼쪽 원형부터 카운트 수만큼 채운다.
 */
function CountGraphic({
                          balls,
                          strikes,
                          outs,
                      }: {
    balls: number
    strikes: number
    outs: number
}) {
    return (
        <div className="count-graphic">
            <CountRow label="B" count={balls} max={3} colorClass="ball" />
            <CountRow label="S" count={strikes} max={2} colorClass="strike" />
            <CountRow label="O" count={outs} max={2} colorClass="out" />
        </div>
    )
}

function CountRow({
                      label,
                      count,
                      max,
                      colorClass,
                  }: {
    label: string
    count: number
    max: number
    colorClass: 'ball' | 'strike' | 'out'
}) {
    return (
        <div className="count-row">
            <strong>{label}</strong>
            <div className="count-dots">
                {Array.from({ length: max }, (_, index) => (
                    <i
                        key={`${label}-${index}`}
                        className={`count-dot ${index < count ? colorClass : ''}`}
                    />
                ))}
            </div>
        </div>
    )
}

/**
 * 베이스 그래픽이다.
 * 1루, 2루, 3루는 마름모이고 홈 베이스만 오각형으로 표시한다.
 */
function BaseGraphic({ bases }: { bases: BaseState | null }) {
    return (
        <div className="base-graphic">
            <span className={`base base-second ${bases?.second ? 'is-on' : ''}`} />
            <span className={`base base-third ${bases?.third ? 'is-on' : ''}`} />
            <span className={`base base-first ${bases?.first ? 'is-on' : ''}`} />

            <span className="home-plate" />

            <span className="mound-mark" />
        </div>
    )
}

/**
 * 보호 모드 하단 데이터 영역이다.
 * "제공 가능한 정보" 칸은 제거하고, 요약 + 최근 흐름만 보여준다.
 */
function ProtectedDataArea({
                               gameDetail,
                           }: {
    gameDetail: Extract<GameDetailResponse, { displayMode: 'PROTECTED' }>
}) {
    return (
        <section className="data-grid protected-data">
            <article className="summary-card">
                <SectionTitle icon="🛡️" title="보호 모드 요약" />
                <h3>{gameDetail.summary.reasonTags[0] ?? '관전 포인트 수집 중'}</h3>
                <p>점수, 이닝별 득점판, 베이스 상황, 플레이 원문은 숨김 처리됩니다.</p>

                <div className="mini-stat-row">
                    <MiniStat
                        label="현재 이닝"
                        value={getProtectedInningLabel(gameDetail.recentPlays, gameDetail.periodLabel)}
                    />
                    <MiniStat label="보호 상태" value="스포일러 차단" />
                    <MiniStat label="팀 정보" value="표시" />
                </div>
            </article>

            <article className="recent-play-card">
                <SectionTitle icon="📋" title="최근 흐름" />
                <ProtectedPlayList plays={gameDetail.recentPlays} />
            </article>
        </section>
    )
}

/**
 * 공개 모드 하단 데이터 영역이다.
 */
function RevealedDataArea({
                              gameDetail,
                          }: {
    gameDetail: Extract<GameDetailResponse, { displayMode: 'REVEALED' }>
}) {
    const latestPlay = gameDetail.recentPlays[0]

    return (
        <section className="data-grid revealed-data">
            <article className="summary-card">
                <SectionTitle icon="📊" title="현재 상황 요약" />
                <h3>{latestPlay?.text ?? '최근 플레이를 수집 중입니다.'}</h3>
                <p>
                    {gameDetail.awayTeam.abbr ?? 'AWAY'} {gameDetail.score.away ?? '-'} :{' '}
                    {gameDetail.score.home ?? '-'} {gameDetail.homeTeam.abbr ?? 'HOME'}
                </p>

                <div className="mini-stat-row">
                    <MiniStat
                        label="현재 이닝"
                        value={getRevealedInningLabel(gameDetail.period, latestPlay)}
                    />
                    <MiniStat
                        label="볼/스트라이크"
                        value={`${latestPlay?.balls ?? '-'}-${latestPlay?.strikes ?? '-'}`}
                    />
                    <MiniStat label="아웃" value={`${latestPlay?.outs ?? '-'}`} />
                </div>
            </article>

            <article className="match-info-card">
                <SectionTitle icon="🧾" title="경기 정보" />
                <InfoLine label="경기 ID" value={`${gameDetail.gameId}`} />
                <InfoLine label="시작 시간" value={formatStartTime(gameDetail.startTime)} />
                <InfoLine label="상태" value={gameDetail.status} />
            </article>

            <article className="recent-play-card">
                <SectionTitle icon="🎞️" title="최근 플레이" />
                <RevealedPlayList plays={gameDetail.recentPlays} />
            </article>
        </section>
    )
}

function SectionTitle({ icon, title }: { icon: string; title: string }) {
    return (
        <div className="section-title">
            <span>{icon}</span>
            <strong>{title}</strong>
        </div>
    )
}

function MiniStat({ label, value }: { label: string; value: string }) {
    return (
        <div className="mini-stat">
            <span>{label}</span>
            <strong>{value}</strong>
        </div>
    )
}

function InfoLine({ label, value }: { label: string; value: string }) {
    return (
        <p className="info-line">
            <span>{label}</span>
            <strong>{value}</strong>
        </p>
    )
}

function formatStartTime(startTime: string): string {
    const date = new Date(startTime)

    if (Number.isNaN(date.getTime())) {
        return startTime
    }

    return date.toLocaleString('ko-KR', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
    })
}

/**
 * 보호 모드 최근 흐름 목록이다.
 */
function ProtectedPlayList({ plays }: { plays: ProtectedPlay[] }) {
    if (plays.length === 0) {
        return <p className="empty-text">아직 표시할 흐름 데이터가 없습니다.</p>
    }

    return (
        <div className="play-list">
            {plays.slice(0, 4).map((play, index) => (
                <article key={`${play.type}-${index}`} className="play-row protected-play-row">
                    <span>{play.inning ?? '-'}회</span>
                    <strong>{play.type}</strong>
                    <p>
                        아웃 {play.outs ?? '-'} · 볼 {play.balls ?? '-'} · 스트라이크 {play.strikes ?? '-'}
                    </p>
                </article>
            ))}
        </div>
    )
}

function RevealedPlayList({ plays }: { plays: RevealedPlay[] }) {
    if (plays.length === 0) {
        return <p className="empty-text">아직 표시할 플레이 데이터가 없습니다.</p>
    }

    return (
        <div className="play-list">
            {plays.slice(0, 4).map((play) => (
                <article key={play.id} className="play-row">
          <span className={`play-badge ${play.scoringPlay ? 'is-scoring' : ''}`}>
            {play.scoringPlay ? '득점' : play.type}
          </span>
                    <span>
            {play.inning ?? '-'}회 {play.inningType ?? ''}
          </span>
                    <strong>{play.type}</strong>
                    <p>
                        {play.text ?? '플레이 설명이 없습니다.'}
                        <b>
                            {play.awayScore ?? '-'} : {play.homeScore ?? '-'}
                        </b>
                    </p>
                </article>
            ))}
        </div>
    )
}

function UpdatePanelHeader() {
    return (
        <div className="update-panel-header">
            <div>
                <h2>경기 변동 알림</h2>
            </div>
        </div>
    )
}

/**
 * 알림은 최신순으로 표시한다.
 */
function LiveUpdateList({ blocks }: { blocks: LiveUpdateBlock[] }) {
    const latestFirstBlocks = [...blocks].reverse()

    if (latestFirstBlocks.length === 0) {
        return (
            <div className="update-empty">
                <strong>아직 변동 알림이 없습니다.</strong>
                <p>경기 변화가 감지되면 이곳에 쌓입니다.</p>
            </div>
        )
    }

    return (
        <div className="update-list">
            {latestFirstBlocks.map((block, index) => (
                <article key={`${block.title}-${block.timeLabel}-${index}`} className="update-item">
                    <time>{block.timeLabel}</time>
                    <div>
                        <strong>{block.title}</strong>
                        <p>{block.description}</p>

                        {block.tags.length > 0 && (
                            <div className="update-tags">
                                {block.tags.slice(0, 2).map((tag) => (
                                    <span key={tag}>{tag}</span>
                                ))}
                            </div>
                        )}
                    </div>
                    <span className="update-arrow">›</span>
                </article>
            ))}
        </div>
    )
}