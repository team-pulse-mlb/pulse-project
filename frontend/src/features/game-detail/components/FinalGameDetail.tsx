import BoxScoreTable from '../../../shared/components/BoxScoreTable';
import Card from '../../../shared/components/Card';
import EventTimeline from './EventTimeline';
import TensionCurve from './TensionCurve';
import ScoringPlayList from './ScoringPlayList';
import type { FinalGameDetailFixture } from '../fixtures/finalGameDetailFixture';
import type { DisplayMode } from '../lib/displayMode';

interface FinalGameDetailProps {
    fixture: FinalGameDetailFixture;
    mode: DisplayMode;
}

/**
 * 종료 경기의 상세 콘텐츠다.
 *
 * 출력 순서:
 * 1. 이닝별 점수 — 공개 모드이며 데이터가 있을 때만
 * 2. 경기 흐름
 * 3. 이벤트 타임라인
 * 4. 득점 플레이 — 공개 모드에서만
 */
function FinalGameDetail({
                             fixture,
                             mode,
                         }: FinalGameDetailProps) {
    const isRevealed = mode === 'REVEALED';

    return (
        <div className="flex flex-col gap-5">
            {/*
       * 이닝별 점수는 최종 점수와 이닝별 득점을 포함하므로
       * 공개 모드에서만 표시한다.
       *
       * inningScores가 null이면 제목과 카드까지
       * 전부 렌더링하지 않는다.
       */}
            {/**
             * 종료 경기 점수판도 제목 없이 압축된 형태로 표시한다.
             * inningScores가 없으면 카드 자체를 만들지 않는다.
             */}
            {isRevealed && fixture.inningScores && (
                <Card flush>
                    <div className="px-3 py-2 sm:px-4 sm:py-2.5">
                        <BoxScoreTable
                            awayLine={fixture.inningScores.awayLine}
                            homeLine={fixture.inningScores.homeLine}
                        />
                    </div>
                </Card>
            )}

            {/*
       * 종료 경기에서만 경기 흐름 그래프를 표시한다.
       *
       * 그래프 안의 이닝 라벨은 mode에 따라
       * 보호 모드에서는 이닝 숫자만,
       * 공개 모드에서는 초·말까지 표시한다.
       */}
            <TensionCurve
                mode={mode}
                points={fixture.tensionPoints}
            />

            {/*
       * 이벤트 타임라인은 보호·공개 모드 모두 표시한다.
       *
       * 보호 모드에서는 EventTimeline 내부에서
       * 초·말을 숨기고 이닝 숫자로만 그룹화한다.
       */}
            <EventTimeline
                mode={mode}
                events={fixture.events}
            />

            {/**
             * 득점 플레이는 결과를 포함하므로 공개 모드에서만 표시한다.
             */}
            {isRevealed && (
                <ScoringPlayList
                    plays={fixture.scoringPlays}
                />
            )}
        </div>
    );
}

export default FinalGameDetail;