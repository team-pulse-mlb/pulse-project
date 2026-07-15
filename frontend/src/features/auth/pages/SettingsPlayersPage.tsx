import {
    type FormEvent,
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
    searchPlayers,
    type PlayerSearchResponse,
} from '../api/playerApi';

import '../styles/myPage.css';
import '../styles/settingsPlayers.css';

/*
 * 한 사용자가 등록할 수 있는 관심 선수 최대 인원입니다.
 *
 * 백엔드에서도 최대 5명을 검증하지만,
 * 프론트에서도 먼저 제한하여 즉시 안내합니다.
 */
const MAX_FAVORITE_PLAYERS = 5;

/*
 * 화면에서 선택된 선수 정보를 표현하는 타입입니다.
 *
 * 기존 관심 선수 조회 응답과 선수 검색 응답의 필드가 조금 다르므로,
 * 이 화면에서 공통으로 사용할 형태로 정리합니다.
 */
interface SelectedPlayer {
    playerId: number;
    fullName: string | null;
    position: string | null;
    teamId: number | null;
    teamName: string | null;
    teamAbbreviation: string | null;
}

function SettingsPlayersPage() {
    const navigate = useNavigate();

    /*
     * 사용자가 검색창에 입력한 영문 선수 이름입니다.
     */
    const [searchKeyword, setSearchKeyword] =
        useState('');

    /*
     * GET /api/players 검색 결과입니다.
     */
    const [searchResults, setSearchResults] =
        useState<PlayerSearchResponse[]>([]);

    /*
     * 현재 화면에서 선택된 관심 선수 목록입니다.
     *
     * 페이지 진입 시 기존 관심 선수로 초기화하고,
     * 검색 결과의 추가 버튼이나 선택 선수의 삭제 버튼으로 변경합니다.
     */
    const [selectedPlayers, setSelectedPlayers] =
        useState<SelectedPlayer[]>([]);

    /*
     * 관심 선수 저장 API가 관심팀 ID도 함께 받으므로,
     * 기존 관심팀 ID를 조회한 뒤 그대로 보관합니다.
     */
    const [selectedTeamIds, setSelectedTeamIds] =
        useState<number[]>([]);

    /*
     * 관심 선수 저장 API가 알림 설정도 함께 받으므로,
     * 기존 알림 설정을 그대로 보관합니다.
     */
    const [
        notificationSettings,
        setNotificationSettings,
    ] = useState<NotificationSettings | null>(
        null,
    );

    const [isLoading, setIsLoading] =
        useState(true);

    const [isSearching, setIsSearching] =
        useState(false);

    const [isSaving, setIsSaving] =
        useState(false);

    const [errorMessage, setErrorMessage] =
        useState('');

    const [infoMessage, setInfoMessage] =
        useState('');

    /*
     * 선택된 선수 ID를 Set으로 변환합니다.
     *
     * 검색 결과에서 이미 관심 선수로 추가된 선수인지
     * 빠르게 확인할 때 사용합니다.
     */
    const selectedPlayerIdSet = useMemo(() => {
        return new Set(
            selectedPlayers.map(
                (player) => player.playerId,
            ),
        );
    }, [selectedPlayers]);

    /*
     * 페이지 진입 시 현재 사용자의 선호 설정을 조회합니다.
     *
     * 가져오는 정보:
     * - 기존 관심팀
     * - 기존 관심 선수
     * - 기존 알림 설정
     */
    useEffect(() => {
        let ignore = false;

        const loadPlayerSettings = async () => {
            setIsLoading(true);
            setErrorMessage('');

            try {
                const preferenceResponse =
                    await getMyPreferences();

                if (ignore) {
                    return;
                }

                setSelectedTeamIds(
                    preferenceResponse.favoriteTeams.map(
                        (team) => team.teamId,
                    ),
                );

                setNotificationSettings(
                    preferenceResponse.notificationSettings,
                );

                setSelectedPlayers(
                    preferenceResponse.favoritePlayers.map(
                        (player) => ({
                            playerId: player.playerId,
                            fullName: player.fullName,
                            position: player.position,
                            teamId: player.teamId,

                            /*
                             * 기존 관심 선수 응답에는 팀 이름과 약어가 없으므로
                             * 처음 진입한 상태에서는 null로 둡니다.
                             *
                             * 이후 검색 결과에서 추가한 선수는
                             * teamName과 teamAbbreviation도 함께 저장합니다.
                             */
                            teamName: null,
                            teamAbbreviation: null,
                        }),
                    ),
                );
            } catch (error) {
                console.error(
                    '관심 선수 설정 조회 오류:',
                    error,
                );

                if (!ignore) {
                    setErrorMessage(
                        '관심 선수 정보를 불러오지 못했습니다.',
                    );
                }
            } finally {
                if (!ignore) {
                    setIsLoading(false);
                }
            }
        };

        void loadPlayerSettings();

        return () => {
            ignore = true;
        };
    }, []);

    /*
     * 검색 폼 제출 시 선수 이름 검색 API를 호출합니다.
     *
     * 입력할 때마다 외부 API가 호출되지 않도록,
     * 검색 버튼 또는 Enter를 눌렀을 때만 검색합니다.
     */
    const handleSearch = async (
        event: FormEvent<HTMLFormElement>,
    ) => {
        event.preventDefault();

        const keyword = searchKeyword.trim();

        setErrorMessage('');
        setInfoMessage('');

        if (!keyword) {
            setSearchResults([]);

            setErrorMessage(
                '검색할 선수의 영문 이름을 입력해 주세요.',
            );

            return;
        }

        if (isSearching) {
            return;
        }

        setIsSearching(true);

        try {
            const response =
                await searchPlayers(keyword);

            setSearchResults(response);

            if (response.length === 0) {
                setInfoMessage(
                    '검색 결과가 없습니다. 선수의 영문 이름을 확인해 주세요.',
                );
            }
        } catch (error) {
            console.error(
                '선수 검색 오류:',
                error,
            );

            setSearchResults([]);

            setErrorMessage(
                '선수 검색에 실패했습니다. 잠시 후 다시 시도해 주세요.',
            );
        } finally {
            setIsSearching(false);
        }
    };

    /*
     * 검색 결과의 선수를 현재 선택 목록에 추가합니다.
     */
    const handleAddPlayer = (
        player: PlayerSearchResponse,
    ) => {
        setErrorMessage('');
        setInfoMessage('');

        setSelectedPlayers(
            (previousPlayers) => {
                const isAlreadySelected =
                    previousPlayers.some(
                        (selectedPlayer) =>
                            selectedPlayer.playerId ===
                            player.playerId,
                    );

                if (isAlreadySelected) {
                    return previousPlayers;
                }

                if (
                    previousPlayers.length >=
                    MAX_FAVORITE_PLAYERS
                ) {
                    setErrorMessage(
                        `관심 선수는 최대 ${MAX_FAVORITE_PLAYERS}명까지 선택할 수 있습니다.`,
                    );

                    return previousPlayers;
                }

                return [
                    ...previousPlayers,
                    {
                        playerId: player.playerId,
                        fullName: player.fullName,
                        position: player.position,
                        teamId: player.teamId,
                        teamName: player.teamName,
                        teamAbbreviation:
                            player.teamAbbreviation,
                    },
                ];
            },
        );
    };

    /*
     * 선택된 관심 선수를 목록에서 제거합니다.
     *
     * 아직 서버에는 반영하지 않고,
     * 저장 버튼을 눌렀을 때 최종 목록을 전송합니다.
     */
    const handleRemovePlayer = (
        playerId: number,
    ) => {
        setErrorMessage('');
        setInfoMessage('');

        setSelectedPlayers(
            (previousPlayers) =>
                previousPlayers.filter(
                    (player) =>
                        player.playerId !== playerId,
                ),
        );
    };

    /*
     * 변경한 관심 선수 목록을 서버에 저장합니다.
     *
     * 관심팀과 알림 설정은 기존 값을 그대로 보내고,
     * selectedPlayerIds만 현재 선택 목록으로 변경합니다.
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
        setInfoMessage('');

        try {
            await updateMyPreferences({
                selectedTeamIds,

                selectedPlayerIds:
                    selectedPlayers.map(
                        (player) => player.playerId,
                    ),

                notificationSettings,
            });

            navigate('/mypage', {
                replace: true,
            });
        } catch (error) {
            console.error(
                '관심 선수 저장 오류:',
                error,
            );

            setErrorMessage(
                '관심 선수를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.',
            );
        } finally {
            setIsSaving(false);
        }
    };

    if (isLoading) {
        return (
            <main className="mypage-shell">
                <section className="mypage-loading-card">
                    관심 선수 정보를 불러오는 중입니다...
                </section>
            </main>
        );
    }

    return (
        <main className="mypage-shell player-settings-page">
            <section className="mypage-single-column">
                <header className="mypage-content-header">
                    <div>
                        <p className="mypage-eyebrow">
                            PLAYER SETTINGS
                        </p>

                        <h1>관심 선수 관리</h1>

                        <p>
                            좋아하는 MLB 선수를 영문 이름으로
                            검색해 최대 5명까지 등록할 수 있습니다.
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

                {infoMessage && (
                    <p className="mypage-message">
                        {infoMessage}
                    </p>
                )}

                {/* 현재 선택한 관심 선수 */}
                <section className="mypage-card">
                    <div className="mypage-card-title-row">
                        <div>
                            <h2>현재 선택한 선수</h2>

                            <p>
                                삭제 버튼을 누르면 선택 목록에서
                                제외할 수 있습니다.
                            </p>
                        </div>

                        <strong>
                            {selectedPlayers.length}
                            {' / '}
                            {MAX_FAVORITE_PLAYERS}
                        </strong>
                    </div>

                    {selectedPlayers.length === 0 ? (
                        <div className="mypage-empty-box">
                            아직 선택한 관심 선수가 없습니다.
                            아래 검색창에서 선수를 찾아 추가해
                            주세요.
                        </div>
                    ) : (
                        <div className="player-selected-list">
                            {selectedPlayers.map(
                                (player) => (
                                    <article
                                        key={player.playerId}
                                        className="player-selected-card"
                                    >
                                        <div className="player-avatar">
                                            {player.fullName
                                                ?.charAt(0)
                                                .toUpperCase() ??
                                                '?'}
                                        </div>

                                        <div className="player-card-text">
                                            <strong>
                                                {player.fullName ??
                                                    `선수 #${player.playerId}`}
                                            </strong>

                                            <span>
                                                {[
                                                    player.position,
                                                    player.teamAbbreviation,
                                                    player.teamName,
                                                ]
                                                    .filter(Boolean)
                                                    .join(' · ') ||
                                                    '선수 정보를 확인 중입니다.'}
                                            </span>
                                        </div>

                                        <button
                                            type="button"
                                            className="player-remove-button"
                                            onClick={() =>
                                                handleRemovePlayer(
                                                    player.playerId,
                                                )
                                            }
                                            disabled={isSaving}
                                            aria-label={`${player.fullName ?? '선수'} 관심 선수 해제`}
                                        >
                                            삭제
                                        </button>
                                    </article>
                                ),
                            )}
                        </div>
                    )}
                </section>

                {/* 선수 검색 및 결과 */}
                <section className="mypage-card">
                    <div className="mypage-card-title-row">
                        <div>
                            <h2>선수 검색</h2>

                            <p>
                                선수의 영문 이름 일부를 입력해
                                검색하세요.
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
                                : '관심 선수 저장'}
                        </button>
                    </div>

                    <form
                        className="player-search-form"
                        onSubmit={handleSearch}
                    >
                        <label
                            htmlFor="player-search"
                            className="player-search-label"
                        >
                            선수 영문 이름
                        </label>

                        <div className="player-search-control">
                            <input
                                id="player-search"
                                type="search"
                                value={searchKeyword}
                                onChange={(event) =>
                                    setSearchKeyword(
                                        event.target.value,
                                    )
                                }
                                placeholder="예: Ohtani, Judge"
                                autoComplete="off"
                                disabled={
                                    isSearching || isSaving
                                }
                            />

                            <button
                                type="submit"
                                disabled={
                                    isSearching ||
                                    isSaving ||
                                    !searchKeyword.trim()
                                }
                            >
                                {isSearching
                                    ? '검색 중...'
                                    : '검색'}
                            </button>
                        </div>
                    </form>

                    {searchResults.length > 0 && (
                        <div className="player-search-results">
                            {searchResults.map(
                                (player) => {
                                    const isSelected =
                                        selectedPlayerIdSet.has(
                                            player.playerId,
                                        );

                                    return (
                                        <article
                                            key={player.playerId}
                                            className={`player-result-card ${
                                                isSelected
                                                    ? 'selected'
                                                    : ''
                                            }`}
                                        >
                                            <div className="player-avatar">
                                                {player.fullName
                                                    .charAt(0)
                                                    .toUpperCase()}
                                            </div>

                                            <div className="player-card-text">
                                                <strong>
                                                    {
                                                        player.fullName
                                                    }
                                                </strong>

                                                <span>
                                                    {[
                                                        player.position,
                                                        player.teamAbbreviation,
                                                        player.teamName,
                                                    ]
                                                        .filter(Boolean)
                                                        .join(' · ') ||
                                                        '소속팀 정보 없음'}
                                                </span>
                                            </div>

                                            <button
                                                type="button"
                                                className="player-add-button"
                                                onClick={() =>
                                                    handleAddPlayer(
                                                        player,
                                                    )
                                                }
                                                disabled={
                                                    isSelected ||
                                                    isSaving
                                                }
                                            >
                                                {isSelected
                                                    ? '추가됨'
                                                    : '+ 추가'}
                                            </button>
                                        </article>
                                    );
                                },
                            )}
                        </div>
                    )}
                </section>
            </section>
        </main>
    );
}

export default SettingsPlayersPage;