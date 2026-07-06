import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { fetchGameDetail } from '../api/gameDetailApi'
import type {
    GameDetailRequestMode,
    GameDetailResponse,
    LiveUpdateBlock,
    ProtectedPlay,
    RevealedPlay,
} from '../types'
import './GameDetailPage.css'

/**
 * 경기 상세 페이지다.
 *
 * 현재 단계의 역할:
 * - /games/:id에서 gameId를 읽는다.
 * - 기본은 protected 모드로 조회한다.
 * - 사용자가 전체 공개를 누르면 revealed 모드로 다시 조회한다.
 * - 왼쪽에는 경기 상세 데이터, 오른쪽에는 liveUpdateBlocks를 누적 알림처럼 표시한다.
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
         * useParams의 id는 타입상 undefined 가능성이 있으므로
         * 검사 이후 gameId 변수에 담아 비동기 함수 안에서도 string으로 안전하게 사용한다.
         */
        const gameId = id

        async function loadGameDetail() {
            setIsLoading(true)
            setErrorMessage(null)

            try {
                /**
                 * protected/revealed 모드에 따라 백엔드 상세 API를 다시 호출한다.
                 * 보호 모드에서는 스포일러 필드가 없고, 공개 모드에서만 팀명/점수/play text가 내려온다.
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

    const matchupTitle = useMemo(() => {
        if (!gameDetail) {
            return '경기 상세'
        }

        /**
         * protected 응답에는 팀명이 없으므로 팀명을 추측해서 만들지 않는다.
         * revealed 응답에서만 실제 팀명을 상단 큰 제목으로 표시한다.
         */
        if (gameDetail.displayMode === 'REVEALED') {
            const awayName = gameDetail.awayTeam.abbr ?? gameDetail.awayTeam.name ?? 'AWAY'
            const homeName = gameDetail.homeTeam.abbr ?? gameDetail.homeTeam.name ?? 'HOME'
            return `${awayName} vs ${homeName}`
        }

        return '스포일러 보호 경기'
    }, [gameDetail])

    const summaryText = useMemo(() => {
        if (!gameDetail) {
            return ''
        }

        /**
         * protected 모드는 reasonTags만 활용해서 안전한 요약 문구를 만든다.
         * 점수, 팀 우세, 결과를 추측하는 문구는 넣지 않는다.
         */
        if (gameDetail.displayMode === 'PROTECTED') {
            const firstTag = gameDetail.summary.reasonTags[0]

            return firstTag
                ? `${firstTag} 흐름이 감지되고 있습니다.`
                : '현재 경기 흐름을 안전하게 확인하고 있습니다.'
        }

        /**
         * revealed 모드에서는 점수와 팀명이 공개되어도 되지만,
         * 내부 추천 점수인 watchScore/baseScore는 화면에 표시하지 않는다.
         */
        return '공개 모드로 전환되어 경기 정보가 표시됩니다.'
    }, [gameDetail])

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
            <div className="game-detail-shell">
                <section className="game-detail-main">
                    <div className="game-detail-topbar">
                        <button
                            type="button"
                            className={`mode-button ${mode === 'protected' ? 'active protected' : ''}`}
                            onClick={() => setMode('protected')}
                        >
                            스포일러 보호
                        </button>

                        <button
                            type="button"
                            className={`mode-button ${mode === 'revealed' ? 'active revealed' : ''}`}
                            onClick={() => {
                                /**
                                 * 공개 모드는 점수와 play text를 보여줄 수 있으므로
                                 * 사용자가 의도적으로 전환했는지 한 번 더 확인한다.
                                 */
                                const confirmed = window.confirm(
                                    '전체 공개로 전환하면 점수와 경기 내용이 표시됩니다. 계속할까요?',
                                )

                                if (confirmed) {
                                    setMode('revealed')
                                }
                            }}
                        >
                            전체 공개
                        </button>
                    </div>

                    <section className="hero-card">
                        <div className="live-badge">LIVE</div>

                        <h1 className="matchup-title">{matchupTitle}</h1>

                        <p className="summary-box">{summaryText}</p>

                        {gameDetail.displayMode === 'PROTECTED' ? (
                            <ProtectedInfoGrid gameDetail={gameDetail} />
                        ) : (
                            <RevealedInfoGrid gameDetail={gameDetail} />
                        )}
                    </section>

                    <section className="content-card">
                        <h2 className="section-title">관전 흐름</h2>

                        {gameDetail.displayMode === 'PROTECTED' ? (
                            <ProtectedPlayList plays={gameDetail.recentPlays} />
                        ) : (
                            <RevealedPlayList plays={gameDetail.recentPlays} />
                        )}
                    </section>
                </section>

                <aside className="game-detail-sidebar">
                    <section className="sidebar-card">
                        <h2 className="sidebar-title">비슷한 경기 추천</h2>
                        <p className="sidebar-description">
                            지금은 임시로 이 영역에 해당 경기의 실시간 변동 알림을 쌓아 보여줍니다.
                        </p>

                        <LiveUpdateBlockList blocks={gameDetail.liveUpdateBlocks} />
                    </section>
                </aside>
            </div>
        </main>
    )
}

/**
 * protected 모드 데이터 카드다.
 *
 * 팀명, 점수, 초/말, play text는 표시하지 않는다.
 */
function ProtectedInfoGrid({
                               gameDetail,
                           }: {
    gameDetail: Extract<GameDetailResponse, { displayMode: 'PROTECTED' }>
}) {
    return (
        <div className="info-grid">
            <InfoCard label="경기 상태" value={gameDetail.status} />
            <InfoCard label="추천 구간" value={gameDetail.periodLabel} />
            <InfoCard
                label="추천 태그"
                value={gameDetail.summary.reasonTags[0] ?? '흐름 감지 중'}
            />
        </div>
    )
}

/**
 * revealed 모드 데이터 카드다.
 *
 * 공개 모드에서는 팀명과 점수를 표시한다.
 * 단, watchScore/baseScore 같은 내부 추천 점수는 표시하지 않는다.
 */
function RevealedInfoGrid({
                              gameDetail,
                          }: {
    gameDetail: Extract<GameDetailResponse, { displayMode: 'REVEALED' }>
}) {
    return (
        <div className="info-grid">
            <InfoCard label="경기 상태" value={gameDetail.status} />
            <InfoCard label="이닝" value={`${gameDetail.period ?? '-'}회`} />
            <InfoCard
                label="점수"
                value={`${gameDetail.awayTeam.abbr ?? 'AWAY'} ${gameDetail.score.away ?? '-'} : ${
                    gameDetail.score.home ?? '-'
                } ${gameDetail.homeTeam.abbr ?? 'HOME'}`}
            />
        </div>
    )
}

function InfoCard({ label, value }: { label: string; value: string }) {
    return (
        <div className="info-card">
            <span className="info-label">{label}</span>
            <strong className="info-value">{value}</strong>
        </div>
    )
}

/**
 * 오른쪽 사이드 영역에 liveUpdateBlocks를 누적 알림처럼 표시한다.
 */
function LiveUpdateBlockList({ blocks }: { blocks: LiveUpdateBlock[] }) {
    if (blocks.length === 0) {
        return (
            <div className="update-card empty">
                <strong className="update-title">변동 내역 없음</strong>
                <p className="update-text">아직 누적된 실시간 변동 알림이 없습니다.</p>
            </div>
        )
    }

    return (
        <div className="update-list">
            {blocks.map((block, index) => (
                <article key={`${block.title}-${index}`} className="update-card">
                    <strong className="update-title">{block.title}</strong>
                    <p className="update-text">{block.description}</p>

                    <div className="update-meta">
                        <span>{block.timeLabel}</span>
                        <span>{block.periodLabel}</span>
                        <span>{block.intensity}</span>
                    </div>

                    {block.tags.length > 0 && (
                        <div className="tag-list">
                            {block.tags.map((tag) => (
                                <span key={tag} className="tag-item">
                  {tag}
                </span>
                            ))}
                        </div>
                    )}
                </article>
            ))}
        </div>
    )
}

/**
 * 보호 모드 최근 경기 상황 목록이다.
 *
 * inningType은 초/말 방향성을 드러낼 수 있으므로 화면에 표시하지 않는다.
 */
function ProtectedPlayList({ plays }: { plays: ProtectedPlay[] }) {
    if (plays.length === 0) {
        return <p className="empty-text">아직 표시할 흐름 데이터가 없습니다.</p>
    }

    return (
        <div className="timeline-list">
            {plays.map((play, index) => (
                <article key={`${play.type}-${index}`} className="timeline-item">
                    <div className="timeline-dot" />

                    <div className="timeline-content">
                        <strong className="timeline-title">{play.type}</strong>
                        <p className="timeline-text">
                            {play.inning ?? '-'}회 · 아웃 {play.outs ?? '-'} · 볼 {play.balls ?? '-'} ·
                            스트라이크 {play.strikes ?? '-'}
                        </p>
                    </div>
                </article>
            ))}
        </div>
    )
}

/**
 * 공개 모드 최근 플레이 목록이다.
 *
 * 공개 모드에서는 play text와 점수 정보를 표시할 수 있다.
 */
function RevealedPlayList({ plays }: { plays: RevealedPlay[] }) {
    if (plays.length === 0) {
        return <p className="empty-text">아직 표시할 플레이 데이터가 없습니다.</p>
    }

    return (
        <div className="timeline-list">
            {plays.map((play) => (
                <article key={play.id} className="timeline-item">
                    <div className="timeline-dot red" />

                    <div className="timeline-content">
                        <strong className="timeline-title">
                            {play.inning ?? '-'}회 {play.inningType ?? ''} · {play.type}
                        </strong>
                        <p className="timeline-text">{play.text ?? '플레이 설명이 없습니다.'}</p>
                        <p className="timeline-score">
                            점수 {play.awayScore ?? '-'} : {play.homeScore ?? '-'}
                        </p>
                    </div>
                </article>
            ))}
        </div>
    )
}