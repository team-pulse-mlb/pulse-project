import { useEffect, useMemo, useState } from 'react';

import { getMe, type MeResponse } from '../api/authApi';

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

type MyPageTab =
    | 'account'
    | 'teams'
    | 'notifications'
    | 'inbox';

const defaultNotificationSettings: NotificationSettings = {
    all: true,
    gameStart: true,
    surge: true,
    gameSwitch: true,
};

function MyPage() {
    /*
     * 왼쪽 사이드바에서 현재 선택된 메뉴입니다.
     *
     * account       : 계정 정보
     * teams         : 관심팀
     * notifications : 알림 설정
     * inbox         : 알림함
     */
    const [activeTab, setActiveTab] =
        useState<MyPageTab>('account');

    /*
     * 로그인한 사용자 기본 정보입니다.
     */
    const [me, setMe] = useState<MeResponse | null>(null);

    /*
     * 전체 팀 목록입니다.
     *
     * 관심팀 변경 화면에서 전체 MLB 팀을 보여주기 위해 사용합니다.
     */
    const [teams, setTeams] = useState<TeamResponse[]>([]);

    /*
     * 현재 사용자가 선택한 관심팀 ID 목록입니다.
     *
     * 백엔드 PUT 요청에는 이 값이 selectedTeamIds로 전달됩니다.
     */
    const [selectedTeamIds, setSelectedTeamIds] =
        useState<number[]>([]);

    /*
     * 현재 사용자의 알림 설정입니다.
     */
    const [notificationSettings, setNotificationSettings] =
        useState<NotificationSettings>(
            defaultNotificationSettings,
        );

    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);

    const [errorMessage, setErrorMessage] = useState('');
    const [successMessage, setSuccessMessage] = useState('');

    /*
     * 선택된 관심팀 ID 목록을 기준으로,
     * 전체 팀 목록에서 실제 선택된 팀 객체만 골라냅니다.
     *
     * 화면 상단의 "내 관심팀" 카드 영역에서 사용합니다.
     */
    const selectedTeams = useMemo(() => {
        return teams.filter((team) =>
            selectedTeamIds.includes(team.teamId),
        );
    }, [teams, selectedTeamIds]);

    /*
     * 마이페이지 진입 시 필요한 데이터를 한 번에 불러옵니다.
     *
     * 1. 내 기본 정보
     * 2. 내 관심팀/알림 설정
     * 3. 전체 팀 목록
     */
    useEffect(() => {
        let ignore = false;

        const loadMyPage = async () => {
            setIsLoading(true);
            setErrorMessage('');
            setSuccessMessage('');

            try {
                const [
                    meResponse,
                    preferenceResponse,
                    teamsResponse,
                ] = await Promise.all([
                    getMe(),
                    getMyPreferences(),
                    getTeams(),
                ]);

                if (ignore) {
                    return;
                }

                setMe(meResponse);
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
                console.error('마이페이지 조회 오류:', error);

                if (!ignore) {
                    setErrorMessage(
                        '마이페이지 정보를 불러오지 못했습니다.',
                    );
                }
            } finally {
                if (!ignore) {
                    setIsLoading(false);
                }
            }
        };

        loadMyPage();

        return () => {
            ignore = true;
        };
    }, []);

    /*
     * 관심팀 선택/해제 처리입니다.
     *
     * 정책:
     * - 이미 선택된 팀을 다시 누르면 해제
     * - 최대 3개까지만 선택 가능
     */
    const handleToggleTeam = (teamId: number) => {
        setErrorMessage('');
        setSuccessMessage('');

        setSelectedTeamIds((prev) => {
            if (prev.includes(teamId)) {
                return prev.filter((id) => id !== teamId);
            }

            if (prev.length >= 3) {
                alert('관심팀은 최대 3개까지 선택할 수 있습니다.');
                return prev;
            }

            return [...prev, teamId];
        });
    };

    /*
     * 전체 알림 토글입니다.
     *
     * 전체 알림을 켜면 개별 알림도 전부 ON,
     * 전체 알림을 끄면 개별 알림도 전부 OFF로 맞춥니다.
     */
    const handleToggleAllNotifications = () => {
        setErrorMessage('');
        setSuccessMessage('');

        setNotificationSettings((prev) => {
            const nextAll = !prev.all;

            return {
                all: nextAll,
                gameStart: nextAll,
                surge: nextAll,
                gameSwitch: nextAll,
            };
        });
    };

    /*
     * 개별 알림 토글입니다.
     *
     * 개별 알림 3개가 모두 켜져 있으면 all=true,
     * 하나라도 꺼져 있으면 all=false로 맞춥니다.
     */
    const handleToggleNotification = (
        name: Exclude<keyof NotificationSettings, 'all'>,
    ) => {
        setErrorMessage('');
        setSuccessMessage('');

        setNotificationSettings((prev) => {
            const nextSettings = {
                ...prev,
                [name]: !prev[name],
            };

            const nextAll =
                nextSettings.gameStart &&
                nextSettings.surge &&
                nextSettings.gameSwitch;

            return {
                ...nextSettings,
                all: nextAll,
            };
        });
    };

    /*
     * 관심팀 / 알림 설정 저장입니다.
     *
     * 관심팀 탭과 알림 설정 탭 모두 같은 저장 API를 사용합니다.
     */
    const handleSavePreferences = async () => {
        setIsSaving(true);
        setErrorMessage('');
        setSuccessMessage('');

        try {
            const response = await updateMyPreferences({
                selectedTeamIds,
                notificationSettings,
            });

            setSelectedTeamIds(
                response.favoriteTeams.map((team) => team.teamId),
            );

            setNotificationSettings(response.notificationSettings);

            setSuccessMessage('설정이 저장되었습니다.');
        } catch (error) {
            console.error('마이페이지 설정 저장 오류:', error);

            setErrorMessage(
                '설정을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.',
            );
        } finally {
            setIsSaving(false);
        }
    };

    /*
     * 관심팀/알림 설정 탭에서만 저장 버튼을 보여줍니다.
     */
    const canSave =
        activeTab === 'teams' ||
        activeTab === 'notifications';

    if (isLoading) {
        return (
            <main className="mypage-shell">
                <section className="mypage-loading-card">
                    마이페이지 정보를 불러오는 중입니다...
                </section>
            </main>
        );
    }

    return (
        <main className="mypage-shell">
            <section className="mypage-layout">
                <aside className="mypage-sidebar">
                    <div className="mypage-sidebar-logo">
                        PULSE
                    </div>

                    <nav className="mypage-menu">
                        <button
                            type="button"
                            className={
                                activeTab === 'account'
                                    ? 'active'
                                    : ''
                            }
                            onClick={() => setActiveTab('account')}
                        >
                            계정 정보
                        </button>

                        <button
                            type="button"
                            className={
                                activeTab === 'teams'
                                    ? 'active'
                                    : ''
                            }
                            onClick={() => setActiveTab('teams')}
                        >
                            관심팀
                        </button>

                        <button
                            type="button"
                            className={
                                activeTab === 'notifications'
                                    ? 'active'
                                    : ''
                            }
                            onClick={() =>
                                setActiveTab('notifications')
                            }
                        >
                            알림 설정
                        </button>

                        <button
                            type="button"
                            className={
                                activeTab === 'inbox'
                                    ? 'active'
                                    : ''
                            }
                            onClick={() => setActiveTab('inbox')}
                        >
                            알림함
                        </button>
                    </nav>
                </aside>

                <section className="mypage-content">
                    <header className="mypage-content-header">
                        <div>
                            <p className="mypage-eyebrow">
                                MY PAGE
                            </p>

                            <h1>
                                {activeTab === 'account' &&
                                    '계정 정보'}
                                {activeTab === 'teams' &&
                                    '관심팀 관리'}
                                {activeTab === 'notifications' &&
                                    '알림 설정'}
                                {activeTab === 'inbox' &&
                                    '알림함'}
                            </h1>

                            <p>
                                {activeTab === 'account' &&
                                    '내 계정 정보를 확인할 수 있습니다.'}
                                {activeTab === 'teams' &&
                                    '관심 있는 팀을 선택하면 개인화 추천에 활용됩니다.'}
                                {activeTab === 'notifications' &&
                                    'PULSE가 알려줄 경기 흐름 알림을 선택합니다.'}
                                {activeTab === 'inbox' &&
                                    '실시간 알림과 지난 알림을 확인하는 공간입니다.'}
                            </p>
                        </div>

                        {canSave && (
                            <button
                                type="button"
                                className="mypage-primary-button"
                                onClick={handleSavePreferences}
                                disabled={isSaving}
                            >
                                {isSaving
                                    ? '저장 중...'
                                    : '설정 저장'}
                            </button>
                        )}
                    </header>

                    {errorMessage && (
                        <p className="mypage-message error">
                            {errorMessage}
                        </p>
                    )}

                    {successMessage && (
                        <p className="mypage-message success">
                            {successMessage}
                        </p>
                    )}

                    {activeTab === 'account' && (
                        <>
                            <section className="mypage-card">
                                <h2>계정 정보</h2>

                                <div className="mypage-account-simple">
                                    <div className="mypage-account-email-block">
                                        <span>이메일</span>
                                        <strong>{me?.email ?? '-'}</strong>
                                    </div>

                                    <div className="mypage-account-inline-actions">
                                        <button
                                            type="button"
                                            onClick={() =>
                                                alert('비밀번호 변경 기능은 추후 구현 예정입니다.')
                                            }
                                        >
                                            비밀번호 변경
                                        </button>

                                        <button
                                            type="button"
                                            className="danger"
                                            onClick={() =>
                                                alert('회원탈퇴 기능은 추후 구현 예정입니다.')
                                            }
                                        >
                                            회원탈퇴
                                        </button>
                                    </div>
                                </div>
                            </section>

                            <section className="mypage-card">
                                <div className="mypage-card-title-row">
                                    <div>
                                        <h2>내 관심팀</h2>
                                        <p>
                                            현재 선택된 관심팀입니다. 변경은 왼쪽 관심팀 메뉴에서 할 수 있습니다.
                                        </p>
                                    </div>

                                    <button
                                        type="button"
                                        className="mypage-text-button"
                                        onClick={() => setActiveTab('teams')}
                                    >
                                        변경하기
                                    </button>
                                </div>

                                {selectedTeams.length === 0 ? (
                                    <div className="mypage-empty-box">
                                        아직 선택한 관심팀이 없습니다.
                                    </div>
                                ) : (
                                    <div className="mypage-preview-strip">
                                        {selectedTeams.map((team) => (
                                            <article
                                                key={team.teamId}
                                                className="mypage-preview-team-card"
                                            >
                                                <div className="mypage-preview-team-logo">
                                                    {team.logoUrl ? (
                                                        <img
                                                            src={team.logoUrl}
                                                            alt={`${team.displayName} 로고`}
                                                        />
                                                    ) : (
                                                        <span>{team.abbreviation}</span>
                                                    )}
                                                </div>

                                                <div>
                                                    <strong>{team.displayName}</strong>
                                                    <span>
                                                        {team.league} · {team.division}
                                                    </span>
                                                </div>
                                            </article>
                                        ))}
                                    </div>
                                )}
                            </section>

                            <section className="mypage-card">
                                <div className="mypage-card-title-row">
                                    <div>
                                        <h2>현재 알림 설정</h2>
                                        <p>
                                            현재 켜져 있는 알림 상태입니다. 변경은 왼쪽 알림 설정 메뉴에서 할 수 있습니다.
                                        </p>
                                    </div>

                                    <button
                                        type="button"
                                        className="mypage-text-button"
                                        onClick={() => setActiveTab('notifications')}
                                    >
                                        변경하기
                                    </button>
                                </div>

                                <div className="mypage-readonly-notification-list">
                                    <div
                                        className={`mypage-readonly-notification-card ${
                                            notificationSettings.gameStart ? 'active' : ''
                                        }`}
                                    >
                                        <div>
                                            <strong>관심팀 경기 시작 알림</strong>
                                            <span>관심팀 경기가 시작되면 알려줍니다.</span>
                                        </div>

                                        <em>{notificationSettings.gameStart ? 'ON' : 'OFF'}</em>
                                    </div>

                                    <div
                                        className={`mypage-readonly-notification-card ${
                                            notificationSettings.surge ? 'active' : ''
                                        }`}
                                    >
                                        <div>
                                            <strong>모멘텀 급상승 알림</strong>
                                            <span>경기 흐름이 급변하면 알려줍니다.</span>
                                        </div>

                                        <em>{notificationSettings.surge ? 'ON' : 'OFF'}</em>
                                    </div>
                                </div>
                            </section>
                        </>
                    )}

                    {activeTab === 'teams' && (
                        <>
                            <section className="mypage-card">
                                <div className="mypage-card-title-row">
                                    <div>
                                        <h2>내 관심팀</h2>
                                        <p>
                                            현재 선택된 팀입니다.
                                            2개 이상이면 가로로 넘겨볼 수 있습니다.
                                        </p>
                                    </div>

                                    <strong>
                                        {selectedTeamIds.length}/3
                                    </strong>
                                </div>

                                {selectedTeams.length === 0 ? (
                                    <div className="mypage-empty-box">
                                        아직 선택한 관심팀이 없습니다.
                                    </div>
                                ) : (
                                    <div className="mypage-selected-strip">
                                        {selectedTeams.map((team) => (
                                            <article
                                                key={team.teamId}
                                                className="mypage-selected-team"
                                            >
                                                <div className="mypage-selected-logo">
                                                    {team.logoUrl ? (
                                                        <img
                                                            src={team.logoUrl}
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
                                                        {team.league} ·{' '}
                                                        {team.division}
                                                    </span>
                                                </div>
                                            </article>
                                        ))}
                                    </div>
                                )}
                            </section>

                            <section className="mypage-card">
                                <div className="mypage-card-title-row">
                                    <div>
                                        <h2>관심팀 변경</h2>
                                        <p>
                                            팀을 클릭해서 선택하거나 해제하세요.
                                        </p>
                                    </div>
                                </div>

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
                                                onClick={() =>
                                                    handleToggleTeam(
                                                        team.teamId,
                                                    )
                                                }
                                            >
                                                <div className="mypage-team-logo">
                                                    {team.logoUrl ? (
                                                        <img
                                                            src={team.logoUrl}
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
                                                        {team.league} ·{' '}
                                                        {team.division}
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
                            </section>
                        </>
                    )}

                    {activeTab === 'notifications' && (
                        <section className="mypage-card">
                            <h2>알림 수신 설정</h2>

                            <div className="mypage-notification-list">
                                <button
                                    type="button"
                                    className={`mypage-notification-card main ${
                                        notificationSettings.all
                                            ? 'active'
                                            : ''
                                    }`}
                                    onClick={
                                        handleToggleAllNotifications
                                    }
                                >
                                    <div>
                                        <strong>
                                            전체 알림 설정
                                        </strong>
                                        <span>
                                            모든 알림을 한 번에 켜거나 끌 수 있습니다.
                                        </span>
                                    </div>

                                    <em>
                                        {notificationSettings.all
                                            ? 'ON'
                                            : 'OFF'}
                                    </em>
                                </button>

                                <button
                                    type="button"
                                    className={`mypage-notification-card ${
                                        notificationSettings.gameStart
                                            ? 'active'
                                            : ''
                                    }`}
                                    onClick={() =>
                                        handleToggleNotification(
                                            'gameStart',
                                        )
                                    }
                                >
                                    <div>
                                        <strong>
                                            관심팀 경기 시작 알림
                                        </strong>
                                        <span>
                                            관심팀 경기가 시작되면 알려줍니다.
                                        </span>
                                    </div>

                                    <em>
                                        {notificationSettings.gameStart
                                            ? 'ON'
                                            : 'OFF'}
                                    </em>
                                </button>

                                <button
                                    type="button"
                                    className={`mypage-notification-card ${
                                        notificationSettings.surge
                                            ? 'active'
                                            : ''
                                    }`}
                                    onClick={() =>
                                        handleToggleNotification(
                                            'surge',
                                        )
                                    }
                                >
                                    <div>
                                        <strong>
                                            모멘텀 급상승 알림
                                        </strong>
                                        <span>
                                            경기 흐름이 급변하면 알려줍니다.
                                        </span>
                                    </div>

                                    <em>
                                        {notificationSettings.surge
                                            ? 'ON'
                                            : 'OFF'}
                                    </em>
                                </button>
                            </div>
                        </section>
                    )}

                    {activeTab === 'inbox' && (
                        <section className="mypage-card">
                            <h2>알림함</h2>

                            <div className="mypage-empty-box">
                                아직 알림함 기능은 연결 전입니다.
                                <br />
                                추후 SSE로 실시간 알림을 받아 이곳에 표시할 수 있습니다.
                            </div>

                            <div className="mypage-sse-note">
                                <strong>SSE 예정 흐름</strong>
                                <p>
                                    서버가 경기 이벤트를 감지하면,
                                    알림 설정이 켜진 사용자에게 실시간으로 알림을 전송합니다.
                                </p>
                            </div>
                        </section>
                    )}
                </section>
            </section>
        </main>
    );
}

export default MyPage;