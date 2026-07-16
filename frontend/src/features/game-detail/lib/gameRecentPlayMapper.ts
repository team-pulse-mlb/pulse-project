import type {
    RecentPlaysResponse,
    RecentPlayResponse,
} from '../api/gameRecentPlayTypes';
import type {
    RecentPlayViewModel,
} from '../model/gameRecentPlayViewModels';

/**
 * 백엔드의 Top/Bottom 값을
 * 화면에서 사용하는 TOP/BOTTOM 형식으로 변환한다.
 */
function normalizeInningType(
    inningType: RecentPlayResponse['inningType'],
): 'TOP' | 'BOTTOM' | null {
    const normalized =
        String(inningType)
            .trim()
            .toUpperCase();

    if (normalized === 'TOP') {
        return 'TOP';
    }

    if (normalized === 'BOTTOM') {
        return 'BOTTOM';
    }

    return null;
}

/**
 * 해당 플레이가 끝난 시점의 점수를
 * 원정팀 점수 - 홈팀 점수 순서로 표시한다.
 *
 * 점수가 수집되지 않은 경우에는 임의의 값을 만들지 않고
 * 하이픈으로 표시한다.
 */
function createScoreLabel(
    play: RecentPlayResponse,
    awayTeamAbbr: string,
    homeTeamAbbr: string,
) {
    const awayScore =
        play.score.away ?? '-';

    const homeScore =
        play.score.home ?? '-';

    return `${awayTeamAbbr} ${awayScore} - ${homeScore} ${homeTeamAbbr}`;
}

/**
 * 최근 플레이 API 응답을 화면 모델로 변환한다.
 */
export function toRecentPlayViewModels(
    response: RecentPlaysResponse,
    awayTeamAbbr: string,
    homeTeamAbbr: string,
): RecentPlayViewModel[] {
    return response.plays
        .map<RecentPlayViewModel | null>(
            (play) => {
                const inningType =
                    normalizeInningType(
                        play.inningType,
                    );

                const hasDisplayableText =
                    play.text.trim().length > 0;

                if (
                    inningType === null
                    || play.inning <= 0
                    || !hasDisplayableText
                ) {
                    return null;
                }

                return {
                    playId: play.playId,
                    inning: play.inning,
                    inningType,
                    text: play.text,
                    scoreLabel:
                        createScoreLabel(
                            play,
                            awayTeamAbbr,
                            homeTeamAbbr,
                        ),
                    /*
                     * 관심 선수 여부는 현재 API 계약에 없으므로
                     * 화면에서 임의로 강조하지 않는다.
                     */
                    highlighted: false,
                };
            },
        )
        .filter(
            (
                play,
            ): play is RecentPlayViewModel =>
                play !== null,
        );
}