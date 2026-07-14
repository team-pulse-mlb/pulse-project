import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import {
    getMe,
    logout,
    type MeResponse,
} from '../api/authApi';

import {
    getMyPreferences,
    updateMyPreferences,
    type FavoriteTeamResponse,
    type NotificationSettings,
} from '../api/preferenceApi';

import '../styles/myPage.css';

/*
 * API 응답을 받기 전 화면에서 사용할 기본 알림 설정입니다.
 *
 * gameSwitch:
 * - 현재 마이페이지에는 노출하지 않습니다.
 * - 다만 기존 백엔드 설정값을 유지해야 하므로 상태에서는 제거하지 않습니다.
 */
const defaultNotificationSettings: NotificationSettings = {
    all: true,
    gameStart: true,
    surge: true,
    gameSwitch: true,
};

function MyPage() {
    const navigate = useNavigate();

    /*
     * 로그인한 사용자 정보입니다.
     * 현재는 이메일 표시에 사용합니다.
     */
    const [me, setMe] = useState<MeResponse | null>(null);

    /*
     * 현재 사용자가 설정한 관심팀 목록입니다.
     *
     * 마이페이지에서는 읽기 전용으로 보여주고,
     * 실제 변경은 /settings/teams에서 처리합니다.
     */
    const [favoriteTeams, setFavoriteTeams] =
        useState<FavoriteTeamResponse[]>([]);

    /*
     * 사용자의 알림 수신 설정입니다.
     *
     * 현재 화면에서는 다음 두 설정만 노출합니다.
     * - 관심팀 경기 시작 알림
     * - 모멘텀 급상승 알림
     */
    const [notificationSettings, setNotificationSettings] =
        useState<NotificationSettings>(
            defaultNotificationSettings,
        );

    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [isLoggingOut, setIsLoggingOut] = useState(false);

    const [errorMessage, setErrorMessage] = useState('');
    const [successMessage, setSuccessMessage] = useState('');

    /*
     * 마이페이지 진입 시 사용자 정보와 선호 설정을 함께 조회합니다.
     */
    useEffect(() => {
        let ignore = false;

        const loadMyPage = async () => {
            setIsLoading(true);
            setErrorMessage('');

            try {
                const [
                    meResponse,
                    preferenceResponse,
                ] = await Promise.all([
                    getMe(),
                    getMyPreferences(),
                ]);

                if (ignore) {
                    return;
                }

                setMe(meResponse);
                setFavoriteTeams(
                    preferenceResponse.favoriteTeams,
                );
                setNotificationSettings(
                    preferenceResponse.notificationSettings,
                );
            } catch (error) {
                console.error(
                    '마이페이지 조회 오류:',
                    error,
                );

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

        void loadMyPage();

        return () => {
            ignore = true;
        };
    }, []);

    /*
     * 개별 알림 설정을 켜거나 끕니다.
     *
     * all은 백엔드와의 기존 계약을 유지하기 위한 값입니다.
     * 세 개의 개별 설정이 모두 켜졌을 때만 true가 됩니다.
     */
    const handleToggleNotification = (
        name: 'gameStart' | 'surge',
    ) => {
        setErrorMessage('');
        setSuccessMessage('');

        setNotificationSettings((previous) => {
            const nextSettings = {
                ...previous,
                [name]: !previous[name],
            };

            return {
                ...nextSettings,

                all:
                    nextSettings.gameStart &&
                    nextSettings.surge &&
                    nextSettings.gameSwitch,
            };
        });
    };

    /*
     * 알림 설정을 저장합니다.
     *
     * updateMyPreferences API는 관심팀 ID와 알림 설정을
     * 함께 요구하므로 현재 관심팀 ID도 그대로 전달합니다.
     */
    const handleSaveNotifications = async () => {
        setIsSaving(true);
        setErrorMessage('');
        setSuccessMessage('');

        try {
            const response =
                await updateMyPreferences({
                    selectedTeamIds:
                        favoriteTeams.map(
                            (team) => team.teamId,
                        ),

                    notificationSettings,
                });

            setFavoriteTeams(
                response.favoriteTeams,
            );

            setNotificationSettings(
                response.notificationSettings,
            );

            setSuccessMessage(
                '알림 설정이 저장되었습니다.',
            );
        } catch (error) {
            console.error(
                '알림 설정 저장 오류:',
                error,
            );

            setErrorMessage(
                '알림 설정을 저장하지 못했습니다.',
            );
        } finally {
            setIsSaving(false);
        }
    };

    /*
     * 서버 로그아웃을 실행합니다.
     *
     * 처리 순서:
     * 1. 서버에서 Refresh Token 폐기
     * 2. 서버가 Refresh Token 쿠키 만료
     * 3. 브라우저의 Access Token 삭제
     * 4. Header에 로그인 상태 변경 알림
     * 5. 홈 화면으로 이동
     */
    const handleLogout = async () => {
        if (isLoggingOut) {
            return;
        }

        setIsLoggingOut(true);
        setErrorMessage('');
        setSuccessMessage('');

        try {
            await logout();

            localStorage.removeItem(
                'accessToken',
            );

            window.dispatchEvent(
                new Event('auth-changed'),
            );

            navigate('/', {
                replace: true,
            });
        } catch (error) {
            console.error(
                '로그아웃 오류:',
                error,
            );

            setErrorMessage(
                '로그아웃하지 못했습니다. 잠시 후 다시 시도해 주세요.',
            );
        } finally {
            setIsLoggingOut(false);
        }
    };

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
            <section className="mypage-single-column">
                <header className="mypage-content-header">
                    <div>
                        <p className="mypage-eyebrow">
                            MY PAGE
                        </p>

                        <h1>마이페이지</h1>

                        <p>
                            계정과 관심팀, 알림 설정을
                            확인할 수 있습니다.
                        </p>
                    </div>
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

                {/* 계정 정보 */}
                <section className="mypage-card">
                    <h2>계정 정보</h2>

                    <div className="mypage-account-simple">
                        <div className="mypage-account-email-block">
                            <span>이메일</span>

                            <strong>
                                {me?.email ?? '-'}
                            </strong>
                        </div>

                        <div className="mypage-account-inline-actions">
                            <button
                                type="button"
                                onClick={() => {
                                    alert(
                                        '비밀번호 변경 기능은 추후 구현 예정입니다.',
                                    );
                                }}
                            >
                                비밀번호 변경
                            </button>

                            <button
                                type="button"
                                onClick={handleLogout}
                                disabled={isLoggingOut}
                            >
                                {isLoggingOut
                                    ? '로그아웃 중...'
                                    : '로그아웃'}
                            </button>

                            <button
                                type="button"
                                className="danger"
                                onClick={() => {
                                    alert(
                                        '회원탈퇴 기능은 추후 구현 예정입니다.',
                                    );
                                }}
                            >
                                회원탈퇴
                            </button>
                        </div>
                    </div>
                </section>

                {/* 알림 설정 */}
                <section className="mypage-card">
                    <div className="mypage-card-title-row">
                        <div>
                            <h2>알림 설정</h2>

                            <p>
                                받고 싶은 경기 알림을
                                선택하세요.
                            </p>
                        </div>

                        <button
                            type="button"
                            className="mypage-primary-button"
                            onClick={handleSaveNotifications}
                            disabled={isSaving}
                        >
                            {isSaving
                                ? '저장 중...'
                                : '알림 설정 저장'}
                        </button>
                    </div>

                    <div className="mypage-notification-list">
                        <button
                            type="button"
                            className={`mypage-notification-card ${
                                notificationSettings.gameStart
                                    ? 'active'
                                    : ''
                            }`}
                            aria-pressed={
                                notificationSettings.gameStart
                            }
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
                                    관심팀의 경기가 시작되면
                                    알려줍니다.
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
                            aria-pressed={
                                notificationSettings.surge
                            }
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
                                    경기 흐름이 급격히 변하면
                                    알려줍니다.
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

                {/* 관심팀 */}
                <section className="mypage-card">
                    <div className="mypage-card-title-row">
                        <div>
                            <h2>내 관심팀</h2>

                            <p>
                                현재 등록된 관심팀입니다.
                                최대 3개까지 설정할 수 있습니다.
                            </p>
                        </div>

                        <button
                            type="button"
                            className="mypage-text-button"
                            onClick={() =>
                                navigate('/settings/teams')
                            }
                        >
                            관심팀 관리
                        </button>
                    </div>

                    {favoriteTeams.length === 0 ? (
                        <div className="mypage-empty-box">
                            아직 등록한 관심팀이 없습니다.
                        </div>
                    ) : (
                        <div className="mypage-preview-strip">
                            {favoriteTeams.map((team) => (
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
                                            <span>
                                                {team.abbreviation}
                                            </span>
                                        )}
                                    </div>

                                    <div>
                                        <strong>
                                            {team.displayName}
                                        </strong>

                                        <span>
                                            {team.league}
                                            {' · '}
                                            {team.division}
                                        </span>
                                    </div>
                                </article>
                            ))}
                        </div>
                    )}
                </section>

                {/* 실제 구현된 별도 알림함으로 이동 */}
                <section className="mypage-card">
                    <div className="mypage-card-title-row">
                        <div>
                            <h2>알림함</h2>

                            <p>
                                받은 경기 알림과 읽지 않은
                                알림을 확인합니다.
                            </p>
                        </div>

                        <button
                            type="button"
                            className="mypage-text-button"
                            onClick={() =>
                                navigate('/notifications')
                            }
                        >
                            알림함 열기
                        </button>
                    </div>
                </section>
            </section>
        </main>
    );
}

export default MyPage;