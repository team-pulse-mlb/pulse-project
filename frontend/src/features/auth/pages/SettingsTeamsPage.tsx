import {
    useEffect,
    useMemo,
    useState,
} from 'react';

import { useNavigate } from 'react-router';

import {
    getMyPreferences,
    updateMyPreferences,
    type NotificationSettings,
} from '../api/preferenceApi';

import {
    getTeams,
    type TeamResponse,
} from '../../../shared/api/teamApi';

import '../styles/myPage.css';
import '../styles/settingsTeams.css';

/*
 * 한 사용자가 등록할 수 있는 관심팀 최대 개수입니다.
 *
 * 프론트에서 먼저 제한해서 사용자에게 즉시 안내하고,
 * 백엔드에서도 동일하게 최대 3개를 검증합니다.
 */
const MAX_FAVORITE_TEAMS = 3;

function SettingsTeamsPage() {
    const navigate = useNavigate();

    /*
     * 서버에서 조회한 전체 MLB 팀 목록입니다.
     */
    const [teams, setTeams] =
        useState<TeamResponse[]>([]);

    /*
     * 현재 화면에서 선택된 관심팀 ID 목록입니다.
     *
     * 사용자가 팀 카드를 클릭하면 이 배열에
     * 팀 ID가 추가되거나 제거됩니다.
     */
    const [
        selectedTeamIds,
        setSelectedTeamIds,
    ] = useState<number[]>([]);

    /*
     * 관심팀 저장 API가 알림 설정도 함께 요구하므로,
     * 조회한 기존 알림 설정을 그대로 보관합니다.
     *
     * 관심팀을 변경하더라도 기존 알림 설정은
     * 덮어쓰거나 초기화하지 않습니다.
     */
    const [
        notificationSettings,
        setNotificationSettings,
    ] = useState<NotificationSettings | null>(
        null,
    );

    const [isLoading, setIsLoading] =
        useState(true);

    const [isSaving, setIsSaving] =
        useState(false);

    const [errorMessage, setErrorMessage] =
        useState('');

    /*
     * 선택된 ID를 실제 팀 객체로 변환합니다.
     *
     * 화면 상단의 현재 선택된 관심팀 카드에서
     * 팀 이름과 로고를 표시할 때 사용합니다.
     */
    const selectedTeams = useMemo(() => {
        return teams.filter((team) =>
            selectedTeamIds.includes(
                team.teamId,
            ),
        );
    }, [teams, selectedTeamIds]);

    /*
     * 페이지 진입 시 다음 두 정보를 동시에 조회합니다.
     *
     * 1. 전체 MLB 팀 목록
     * 2. 현재 사용자의 관심팀 및 알림 설정
     */
    useEffect(() => {
        let ignore = false;

        const loadTeamSettings = async () => {
            setIsLoading(true);
            setErrorMessage('');

            try {
                const [
                    teamsResponse,
                    preferenceResponse,
                ] = await Promise.all([
                    getTeams(),
                    getMyPreferences(),
                ]);

                if (ignore) {
                    return;
                }

                setTeams(teamsResponse);

                setSelectedTeamIds(
                    preferenceResponse.favoriteTeams.map(
                        (team) => team.teamId,
                    ),
                );

                setNotificationSettings(
                    preferenceResponse.notificationSettings,
                );
            } catch (error) {
                console.error(
                    '관심팀 설정 조회 오류:',
                    error,
                );

                if (!ignore) {
                    setErrorMessage(
                        '관심팀 정보를 불러오지 못했습니다.',
                    );
                }
            } finally {
                if (!ignore) {
                    setIsLoading(false);
                }
            }
        };

        void loadTeamSettings();

        return () => {
            ignore = true;
        };
    }, []);

    /*
     * 팀 카드 선택 및 선택 해제를 처리합니다.
     *
     * 이미 선택된 팀:
     * - 다시 클릭하면 관심팀에서 제거합니다.
     *
     * 선택되지 않은 팀:
     * - 현재 선택 개수가 3개 미만이면 추가합니다.
     * - 이미 3개라면 추가하지 않고 안내 문구를 표시합니다.
     */
    const handleToggleTeam = (
        teamId: number,
    ) => {
        setErrorMessage('');

        setSelectedTeamIds(
            (previousTeamIds) => {
                const isAlreadySelected =
                    previousTeamIds.includes(
                        teamId,
                    );

                if (isAlreadySelected) {
                    return previousTeamIds.filter(
                        (selectedTeamId) =>
                            selectedTeamId !==
                            teamId,
                    );
                }

                if (
                    previousTeamIds.length >=
                    MAX_FAVORITE_TEAMS
                ) {
                    setErrorMessage(
                        `관심팀은 최대 ${MAX_FAVORITE_TEAMS}개까지 선택할 수 있습니다.`,
                    );

                    return previousTeamIds;
                }

                return [
                    ...previousTeamIds,
                    teamId,
                ];
            },
        );
    };

    /*
     * 변경한 관심팀을 서버에 저장합니다.
     *
     * 기존 알림 설정은 변경하지 않고 그대로 전달합니다.
     * 저장 성공 후 마이페이지로 돌아가 변경된 팀을 확인합니다.
     */
    const handleSave = async () => {
        if (isSaving) {
            return;
        }

        if (!notificationSettings) {
            setErrorMessage(
                '알림 설정 정보를 불러오지 못해 저장할 수 없습니다.',
            );

            return;
        }

        setIsSaving(true);
        setErrorMessage('');

        try {
            await updateMyPreferences({
                selectedTeamIds,
                notificationSettings,
            });

            navigate('/mypage', {
                replace: true,
            });
        } catch (error) {
            console.error(
                '관심팀 저장 오류:',
                error,
            );

            setErrorMessage(
                '관심팀을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.',
            );
        } finally {
            setIsSaving(false);
        }
    };

    if (isLoading) {
        return (
            <main className="mypage-shell">
                <section className="mypage-loading-card">
                    관심팀 정보를 불러오는 중입니다...
                </section>
            </main>
        );
    }

    return (
        <main className="mypage-shell team-settings-page">
            <section className="mypage-single-column">
                <header className="mypage-content-header">
                    <div>
                        <p className="mypage-eyebrow">
                            TEAM SETTINGS
                        </p>

                        <h1>관심팀 관리</h1>

                        <p>
                            관심 있는 MLB 팀을 최대
                            3개까지 선택할 수 있습니다.
                        </p>
                    </div>

                    <button
                        type="button"
                        className="mypage-text-button"
                        onClick={() =>
                            navigate('/mypage')
                        }
                        disabled={isSaving}
                    >
                        마이페이지로 돌아가기
                    </button>
                </header>

                {errorMessage && (
                    <p className="mypage-message error">
                        {errorMessage}
                    </p>
                )}

                {/* 현재 선택된 관심팀 */}
                <section className="mypage-card">
                    <div className="mypage-card-title-row">
                        <div>
                            <h2>현재 선택한 팀</h2>

                            <p>
                                선택된 팀을 다시 누르면
                                관심팀에서 해제할 수 있습니다.
                            </p>
                        </div>

                        <strong>
                            {selectedTeamIds.length}
                            {' / '}
                            {MAX_FAVORITE_TEAMS}
                        </strong>
                    </div>

                    {selectedTeams.length === 0 ? (
                        <div className="mypage-empty-box">
                            아직 선택한 관심팀이 없습니다.
                            아래 팀 목록에서 관심팀을
                            선택해 주세요.
                        </div>
                    ) : (
                        <div className="mypage-selected-strip">
                            {selectedTeams.map(
                                (team) => (
                                    <article
                                        key={team.teamId}
                                        className="mypage-selected-team"
                                    >
                                        <div className="mypage-selected-logo">
                                            {team.logoUrl ? (
                                                <img
                                                    src={
                                                        team.logoUrl
                                                    }
                                                    alt={`${team.displayName} 로고`}
                                                />
                                            ) : (
                                                <span>
                                                    {
                                                        team.abbreviation
                                                    }
                                                </span>
                                            )}
                                        </div>

                                        <div>
                                            <strong>
                                                {
                                                    team.displayName
                                                }
                                            </strong>

                                            <span>
                                                {
                                                    team.league
                                                }
                                                {' · '}
                                                {
                                                    team.division
                                                }
                                            </span>
                                        </div>
                                    </article>
                                ),
                            )}
                        </div>
                    )}
                </section>

                {/* 전체 팀 선택 목록 */}
                <section className="mypage-card">
                    <div className="mypage-card-title-row">
                        <div>
                            <h2>팀 선택</h2>

                            <p>
                                카드를 눌러 관심팀을
                                선택하거나 해제하세요.
                            </p>
                        </div>

                        <button
                            type="button"
                            className="mypage-primary-button"
                            onClick={handleSave}
                            disabled={
                                isSaving ||
                                !notificationSettings
                            }
                        >
                            {isSaving
                                ? '저장 중...'
                                : '관심팀 저장'}
                        </button>
                    </div>

                    {teams.length === 0 ? (
                        <div className="mypage-empty-box">
                            표시할 팀 정보가 없습니다.
                        </div>
                    ) : (
                        <div className="mypage-team-grid">
                            {teams.map((team) => {
                                const isSelected =
                                    selectedTeamIds.includes(
                                        team.teamId,
                                    );

                                return (
                                    <button
                                        key={team.teamId}
                                        type="button"
                                        className={`mypage-team-card ${
                                            isSelected
                                                ? 'selected'
                                                : ''
                                        }`}
                                        aria-pressed={
                                            isSelected
                                        }
                                        disabled={
                                            isSaving
                                        }
                                        onClick={() =>
                                            handleToggleTeam(
                                                team.teamId,
                                            )
                                        }
                                    >
                                        <div className="mypage-team-logo">
                                            {team.logoUrl ? (
                                                <img
                                                    src={
                                                        team.logoUrl
                                                    }
                                                    alt={`${team.displayName} 로고`}
                                                />
                                            ) : (
                                                <span>
                                                    {
                                                        team.abbreviation
                                                    }
                                                </span>
                                            )}
                                        </div>

                                        <div className="mypage-team-text">
                                            <strong>
                                                {
                                                    team.displayName
                                                }
                                            </strong>

                                            <span>
                                                {
                                                    team.league
                                                }
                                                {' · '}
                                                {
                                                    team.division
                                                }
                                            </span>
                                        </div>

                                        <em>
                                            {isSelected
                                                ? '선택됨'
                                                : '+'}
                                        </em>
                                    </button>
                                );
                            })}
                        </div>
                    )}
                </section>
            </section>
        </main>
    );
}

export default SettingsTeamsPage;