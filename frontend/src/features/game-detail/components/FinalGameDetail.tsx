import BoxScoreTable from '../../../shared/components/BoxScoreTable';
import Card from '../../../shared/components/Card';
import type { FinalGameDetailViewModel } from '../model/gameDetailViewModels';
import TensionCurve from './TensionCurve';

interface FinalGameDetailProps {
    data: FinalGameDetailViewModel;
}

/**
 * 종료 경기의 상세 콘텐츠다.
 *
 * 현재 이 컴포넌트는 다음 영역만 담당한다.
 * - 공개 모드 이닝별 점수판
 * - 데이터가 있을 때 긴장도 그래프
 *
 * 이벤트 타임라인과 최근 플레이는
 * GameDetailPage에서 별도 컴포넌트로 순서에 맞게 배치한다.
 */
function FinalGameDetail({
                             data,
                         }: FinalGameDetailProps) {
    const isRevealed =
        data.displayMode === 'REVEALED';

    return (
        <div className="flex flex-col gap-5">
            {isRevealed
                && data.inningScores && (
                    <Card flush>
                        <div className="px-3 py-2 sm:px-4 sm:py-2.5">
                            <BoxScoreTable
                                awayLine={
                                    data.inningScores.awayLine
                                }
                                homeLine={
                                    data.inningScores.homeLine
                                }
                            />
                        </div>
                    </Card>
                )}

            {/*
             * 그래프 데이터가 없으면 카드 자체를 렌더링하지 않는다.
             */}
            {data.tensionPoints.length > 0 && (
                <TensionCurve
                    mode={data.displayMode}
                    points={data.tensionPoints}
                />
            )}
        </div>
    );
}

export default FinalGameDetail;