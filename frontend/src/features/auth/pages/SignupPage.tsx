import axios from 'axios';

import {
    type ChangeEvent,
    type SubmitEventHandler,
    useEffect,
    useState,
} from 'react';

import { useNavigate } from 'react-router';

import '../styles/signup.css';

import { 
    signupMember, 
    checkEmailDuplicate,
    sendEmailCode, 
    verifyEmailCode 
} from '../api/memberApi';

import {
    getTeams,
    type TeamResponse,
} from '../../../shared/api/teamApi';


interface SignupFormData {
    email: string;
    verificationCode: string;
    password: string;
    passwordConfirm: string;
}

interface SignupErrors {
    email: string;
    verificationCode: string;
    password: string;
    passwordConfirm: string;
}

type SignupStep = 1 | 2 | 3;

interface NotificationSettings {
    // 전체 알림 ON/OFF
    all: boolean;

    // 관심팀 경기가 시작되면 알림을 받을지 여부
    gameStart: boolean;

    // 경기 흐름이 급상승할 때 알림을 받을지 여부
    surge: boolean;

    // 사용자가 보던 경기보다 더 추천할 만한 경기로 전환할 때 알림을 받을지 여부
    gameSwitch: boolean;
}

const initialFormData: SignupFormData = {
    email: '',
    verificationCode: '',
    password: '',
    passwordConfirm: '',
};

const initialErrors: SignupErrors = {
    email: '',
    verificationCode: '',
    password: '',
    passwordConfirm: '',
};

function SignupPage() {
    const navigate = useNavigate();

    // 현재 회원가입 단계
    // 1: 계정 정보 입력
    // 2: 관심팀 선택
    // 3: 알림 설정
    const [signupStep, setSignupStep] =
        useState<SignupStep>(1);

    // 사용자가 선택한 관심팀 id 목록
    // 건너뛰면 빈 배열 [] 상태로 가입 진행
    const [selectedTeamIds, setSelectedTeamIds] =
        useState<number[]>([]);

    /*
     * 백엔드 GET /api/teams에서 받아온 전체 팀 목록입니다.
     *
     * 기존에는 프론트에서 임시 하드코딩했지만,
     * 이제 DB teams 테이블 기준으로 받아옵니다.
     */
    const [teams, setTeams] =
        useState<TeamResponse[]>([]);

    /*
     * 팀 목록 로딩 상태입니다.
     *
     * API 요청 중일 때 "팀 목록을 불러오는 중입니다" 같은 메시지를 보여주는 데 사용합니다.
     */
    const [isTeamsLoading, setIsTeamsLoading] =
        useState(false);

    /*
     * 팀 목록 조회 실패 메시지입니다.
     */
    const [teamLoadError, setTeamLoadError] =
        useState('');

    // 알림 설정 기본값
    // 사용자가 알림 단계를 건너뛰거나 따로 수정하지 않으면 이 값이 기본 적용됨
    const [notificationSettings, setNotificationSettings] =
        useState<NotificationSettings>({
            // 기본값은 전체 ON
            all: true,
            
            // 유저가 따로 끄지 않으면 기본 알림을 받는 방향
            gameStart: true,
            surge: true,
            gameSwitch: true,
        });
    
    const [formData, setFormData] =
        useState<SignupFormData>(initialFormData);

    const [errors, setErrors] =
        useState<SignupErrors>(initialErrors);

    const [isCodeSent, setIsCodeSent] = useState(false);

    const [emailChecked, setEmailChecked] = useState(false);
    const [emailAvailable, setEmailAvailable] =
        useState<boolean | null>(null);
    const [emailCheckMessage, setEmailCheckMessage] = useState('');

    const [isEmailVerified, setIsEmailVerified] = useState(false);
    const [verificationMessage, setVerificationMessage] = useState('');

    /*
     * 회원가입 화면 진입 시 팀 목록을 한 번 불러옵니다.
     *
     * 관심팀 선택은 Step 2에서 사용하지만,
     * 미리 불러와도 데이터가 30개뿐이라 부담이 작습니다.
     */
    useEffect(() => {
        let ignore = false;

        const fetchTeams = async () => {
            setIsTeamsLoading(true);
            setTeamLoadError('');

            try {
                const result = await getTeams();

                if (!ignore) {
                    setTeams(result);
                }
            } catch (error) {
                console.error('팀 목록 조회 오류:', error);

                if (!ignore) {
                    setTeamLoadError(
                        '팀 목록을 불러오지 못했습니다.',
                    );
                }
            } finally {
                if (!ignore) {
                    setIsTeamsLoading(false);
                }
            }
        };

        fetchTeams();

        /*
         * 컴포넌트가 사라진 뒤 setState가 실행되는 것을 막기 위한 정리 함수입니다.
         */
        return () => {
            ignore = true;
        };
    }, []);

    // 모든 입력창의 값을 formData에 저장
    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
        const { name, value } = e.target;

        setFormData((prev) => ({
            ...prev,
            [name]: value,
        }));

        // 입력을 다시 시작하면 기존 오류 메시지 제거
        setErrors((prev) => ({
            ...prev,
            [name]: '',
        }));

        // 이메일을 수정하면 기존 중복확인 결과 초기화
        if (name === 'email') {
            setEmailChecked(false);
            setEmailAvailable(null);
            setEmailCheckMessage('');
            setIsEmailVerified(false);
            setVerificationMessage('');
        }

        if (name === 'verificationCode') {
            setIsEmailVerified(false);
            setVerificationMessage('');
        }
    };

    // 이메일 형식 검사
    const validateEmail = (email: string): boolean => {
        const emailPattern =
            /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

        return emailPattern.test(email);
    };

    // 비밀번호 형식 검사
    const validatePassword = (password: string): boolean => {
        // 영문과 숫자를 포함한 8~20자
        const passwordPattern =
            /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d!@#$%^&*()_+\-=]{8,20}$/;

        return passwordPattern.test(password);
    };

    // 인증번호 받기 버튼
    // → 이메일 형식 검사
    // → 서버에서 이메일 중복확인
    // → 사용 가능할 때만 인증번호 입력창 표시
    const handleSendCode = async () => {
        const email = formData.email.trim();

        // 1. 이메일 빈 값 검사
        if (email === '') {
            setErrors((prev) => ({
                ...prev,
                email: '이메일을 입력해 주세요.',
            }));
            return;
        }

        // 2. 이메일 형식 검사
        if (!validateEmail(email)) {
            setErrors((prev) => ({
                ...prev,
                email: '올바른 이메일 형식으로 입력해 주세요.',
            }));
            return;
        }

        setErrors((prev) => ({
            ...prev,
            email: '',
        }));

        try {
            // 3. 먼저 가입된 이메일인지 확인
            const checkResult =
                await checkEmailDuplicate(email);

            setEmailChecked(true);
            setEmailAvailable(checkResult.available);
            setEmailCheckMessage(checkResult.message);

            // 이미 가입된 이메일이면 여기서 중단
            if (!checkResult.available) {
                setIsCodeSent(false);
                return;
            }

            // 4. Spring 서버에 인증번호 발급 요청
            // 서버가 번호를 생성하고 Redis에 5분 동안 저장
            const sendResult =
                await sendEmailCode(email);

            // 앞뒤 공백을 제거한 이메일로 확정
            setFormData((prev) => ({
                ...prev,
                email,
                verificationCode: '',
            }));

            // 이전 인증 상태 초기화
            setIsEmailVerified(false);
            setVerificationMessage('');

            // 인증번호 입력창 표시
            setIsCodeSent(true);

            // "인증번호를 발급했습니다." 표시
            setEmailCheckMessage(sendResult.message);
        } catch (error) {
            console.error(
                '이메일 인증번호 발급 오류:',
                error,
            );

            setEmailChecked(false);
            setEmailAvailable(false);
            setIsCodeSent(false);
            setIsEmailVerified(false);

            if (axios.isAxiosError(error)) {
                const message =
                    error.response?.data?.message ??
                    '인증번호 발급 중 오류가 발생했습니다.';

                setEmailCheckMessage(message);
                return;
            }

            setEmailCheckMessage(
                '알 수 없는 오류가 발생했습니다.',
            );
        }
    };

    // 이메일을 다시 입력할 때 인증 상태 전체 초기화
    const handleResetEmail = () => {
        setFormData((prev) => ({
            ...prev,
            email: '',
            verificationCode: '',
        }));

        setErrors((prev) => ({
            ...prev,
            email: '',
            verificationCode: '',
        }));

        setEmailChecked(false);
        setEmailAvailable(null);
        setEmailCheckMessage('');

        setIsCodeSent(false);
        setIsEmailVerified(false);
        setVerificationMessage('');
    };

    // 인증번호 확인 버튼
    const handleVerifyCode = async () => {
        const verificationCode =
            formData.verificationCode.trim();

        if (verificationCode === '') {
            setErrors((prev) => ({
                ...prev,
                verificationCode: '인증번호를 입력해 주세요.',
            }));
            return;
        }

        if (!/^\d{6}$/.test(verificationCode)) {
            setErrors((prev) => ({
                ...prev,
                verificationCode: '인증번호 6자리를 입력해 주세요.',
            }));
            return;
        }

        try {
            // Spring 서버에서 Redis의 인증번호와 비교
            const result = await verifyEmailCode(
                formData.email,
                verificationCode,
            );

            setErrors((prev) => ({
                ...prev,
                verificationCode: '',
            }));

            // 서버가 인증 성공을 반환했을 때만 완료 처리
            setIsEmailVerified(result.verified);
            setVerificationMessage(result.message);
        } catch (error) {
            console.error(
                '이메일 인증번호 확인 오류:',
                error,
            );

            setIsEmailVerified(false);
            setVerificationMessage('');

            if (axios.isAxiosError(error)) {
                const message =
                    error.response?.data?.message ??
                    '인증번호 확인 중 오류가 발생했습니다.';

                setErrors((prev) => ({
                    ...prev,
                    verificationCode: message,
                }));
                return;
            }

            setErrors((prev) => ({
                ...prev,
                verificationCode:
                    '알 수 없는 오류가 발생했습니다.',
            }));
        }
    };

    // 회원가입 버튼
    const handleAccountStepSubmit: SubmitEventHandler<HTMLFormElement> = async (e) => {
        e.preventDefault();

        const nextErrors: SignupErrors = {
            email: '',
            verificationCode: '',
            password: '',
            passwordConfirm: '',
        };

        const email = formData.email.trim();

        if (email === '') {
            nextErrors.email = '이메일을 입력해 주세요.';
        } else if (!validateEmail(email)) {
            nextErrors.email = '올바른 이메일 형식으로 입력해 주세요.';
        }

        if (formData.password === '') {
            nextErrors.password = '비밀번호를 입력해 주세요.';
        } else if (!validatePassword(formData.password)) {
            nextErrors.password =
                '영문과 숫자를 포함하여 8~20자로 입력해 주세요.';
        }

        if (formData.passwordConfirm === '') {
            nextErrors.passwordConfirm =
                '비밀번호를 다시 입력해 주세요.';
        } else if (
            formData.password !== formData.passwordConfirm
        ) {
            nextErrors.passwordConfirm =
                '비밀번호가 일치하지 않습니다.';
        }

        if (!emailChecked) {
            nextErrors.email =
                '이메일 확인을 진행해 주세요.';
        } else if (!emailAvailable) {
            nextErrors.email =
                '사용 가능한 이메일을 입력해 주세요.';
        } else if (!isCodeSent) {
            nextErrors.email =
                '인증번호를 받아 주세요.';
        } else if (!isEmailVerified) {
            nextErrors.verificationCode =
                '이메일 인증을 완료해 주세요.';
        }

        setErrors(nextErrors);

        const hasError = Object.values(nextErrors).some(
            (message) => message !== '',
        );

        if (hasError) {
            return;
        }

        setSignupStep(2);
    };

    /*
     * 관심팀 선택/해제 처리.
     *
     * P1 정책:
     * - 관심팀은 최대 3개까지 선택 가능
     * - 이미 선택된 팀을 다시 누르면 선택 해제
     */
    const handleToggleTeam = (teamId: number) => {
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

    const handleSkipTeams = () => {
        setSelectedTeamIds([]);
        setSignupStep(3);
    };

    const handleTeamStepNext = () => {
        setSignupStep(3);
    };

    const handleToggleAllNotifications = () => {
        setNotificationSettings((prev) => {
            // 현재 전체 알림이 true면 false로, false면 true로 변경
            const nextAll = !prev.all;

            return {
                // 전체 알림 상태 변경
                all: nextAll,

                // 전체 알림을 끄면 개별 알림도 모두 OFF
                // 전체 알림을 켜면 개별 알림도 모두 ON
                gameStart: nextAll,
                surge: nextAll,
                gameSwitch: nextAll,
            };
        });
    };

    const handleToggleNotification = (
        name: Exclude<keyof NotificationSettings, 'all'>,
    ) => {
        setNotificationSettings((prev) => {
            const nextSettings = {
                ...prev,

                // name으로 들어온 알림 설정만 true/false 반전
                // 예: gameStart가 true면 false, false면 true
                [name]: !prev[name],
            };

            // 개별 알림 3개가 모두 ON이면 전체 알림도 ON
            // 하나라도 OFF면 전체 알림은 OFF
            const nextAll =
                nextSettings.gameStart &&
                nextSettings.surge &&
                nextSettings.gameSwitch;

            return {
                ...nextSettings,

                // 개별 알림 상태에 맞춰 전체 알림 상태도 자동 갱신
                all: nextAll,
            };
        });
};

    const handleFinalSignup = async () => {
        const email = formData.email.trim();

        /*
         * 최종 회원가입 요청 데이터.
         *
         * 백엔드 SignupRequest와 필드명을 정확히 맞춘다.
         *
         * selectedTeamIds:
         * - 회원가입 Step 2에서 선택한 팀 ID 목록
         *
         * notificationSettings:
         * - 회원가입 Step 3에서 설정한 알림 값
         * - all은 DB 저장 대상은 아니지만, 백엔드 DTO에서 받을 수 있도록 함께 보낸다.
         */
        const signupRequest = {
            email,
            password: formData.password,
            selectedTeamIds,
            notificationSettings: {
                all: notificationSettings.all,
                gameStart: notificationSettings.gameStart,
                surge: notificationSettings.surge,
                gameSwitch: notificationSettings.gameSwitch,
            },
        };

        console.log('최종 회원가입 요청:', signupRequest);

        try {
            /*
             * 회원 계정, 관심팀, 알림 설정을 한 번에 회원가입 API로 전송한다.
             */
            const result = await signupMember(signupRequest);

            console.log('회원가입 응답:', result);

            alert(result.message ?? '회원가입이 완료되었습니다.');

            navigate('/');

        } catch (error) {
            console.error('회원가입 요청 오류:', error);

            if (axios.isAxiosError(error)) {
                const message =
                    error.response?.data?.message ??
                    '회원가입 처리 중 오류가 발생했습니다.';

                alert(message);
                return;
            }

            alert('알 수 없는 오류가 발생했습니다.');
        }
    };
    
    const emailMessage =
        errors.email || emailCheckMessage;

    return (
        <main className="signup-page">
            <section className="signup-container">
                <header className="signup-header">
                    <h1 className="signup-logo">PULSE</h1>
                    
                    {/* 현재 회원가입 단계 표시 */}
                    <div className="signup-step-indicator">
                        <span>{signupStep}/3</span>

                        <strong>
                            {signupStep === 1 && '계정 정보'}
                            {signupStep === 2 && '관심팀 선택'}
                            {signupStep === 3 && '알림 설정'}
                        </strong>
                    </div>

                    <h2>회원가입</h2>

                    <p>
                        {signupStep === 1 &&
                            '이메일 인증과 비밀번호 설정을 완료해 주세요.'}
                        {signupStep === 2 &&
                            '관심 있는 팀을 선택하면 개인화 추천에 활용할 수 있어요.'}
                        {signupStep === 3 &&
                            '알림 수신 여부를 설정하고 가입을 완료해 주세요.'}
                    </p>
                </header>

                {signupStep === 1 && (
                    <form
                        className="signup-form"
                        onSubmit={handleAccountStepSubmit}
                        noValidate
                    >
                        <div className="signup-field">
                            <label htmlFor="email">이메일</label>

                            <div className="signup-input-row">
                                <input
                                    id="email"
                                    name="email"
                                    type="email"
                                    value={formData.email}
                                    onChange={handleChange}
                                    placeholder="example@email.com"
                                    autoComplete="email"
                                    required
                                    readOnly={isCodeSent}
                                    className={
                                        errors.email
                                            ? 'signup-input-error'
                                            : ''
                                    }
                                />

                                <button
                                    type="button"
                                    className="email-action-button"
                                    onClick={
                                        isCodeSent
                                            ? handleResetEmail
                                            : handleSendCode
                                    }
                                >
                                    {isCodeSent
                                        ? '다시 입력'
                                        : '인증번호 받기'}
                                </button>
                            </div>

                            <div className="signup-email-message-area">
                                {emailMessage && (
                                    <p
                                        className={`signup-email-message ${
                                            errors.email || emailAvailable === false
                                                ? 'error'
                                                : 'success'
                                        }`}
                                    >
                                        {emailMessage}
                                    </p>
                                )}
                            </div>
                            
                        </div>

                        {isCodeSent && (
                            <div className="signup-field verification-field">
                                <label htmlFor="verificationCode">
                                    인증번호
                                </label>

                                <div className="signup-input-row">
                                    <input
                                        id="verificationCode"
                                        name="verificationCode"
                                        type="text"
                                        inputMode="numeric"
                                        maxLength={6}
                                        value={formData.verificationCode}
                                        onChange={handleChange}
                                        placeholder="인증번호 6자리"
                                        autoComplete="one-time-code"
                                        required={isCodeSent}
                                        readOnly={isEmailVerified}
                                        className={
                                            errors.verificationCode
                                                ? 'signup-input-error'
                                                : ''
                                        }
                                    />

                                    <button
                                        type="button"
                                        className="email-action-button"
                                        onClick={handleVerifyCode}
                                        disabled={isEmailVerified}
                                    >
                                        {isEmailVerified
                                            ? '인증 완료'
                                            : '인증 확인'}
                                    </button>
                                </div>

                                <div className="signup-email-message-area">
                                    {(errors.verificationCode ||
                                        verificationMessage) && (
                                        <p
                                            className={`signup-email-message ${
                                                errors.verificationCode
                                                    ? 'error'
                                                    : 'success'
                                            }`}
                                        >
                                            {errors.verificationCode ||
                                                verificationMessage}
                                        </p>
                                    )}
                                </div>
                            </div>
                        )}

                        <div className="signup-field">
                            <label htmlFor="password">비밀번호</label>

                            <input
                                id="password"
                                name="password"
                                type="password"
                                value={formData.password}
                                onChange={handleChange}
                                placeholder="영문과 숫자를 포함한 8자 이상"
                                autoComplete="new-password"
                                className={
                                    errors.password
                                        ? 'signup-input-error'
                                        : ''
                                }
                            />

                            <p className="signup-error-message">
                                {errors.password}
                            </p>
                        </div>

                        <div className="signup-field">
                            <label htmlFor="passwordConfirm">
                                비밀번호 확인
                            </label>

                            <input
                                id="passwordConfirm"
                                name="passwordConfirm"
                                type="password"
                                value={formData.passwordConfirm}
                                onChange={handleChange}
                                placeholder="비밀번호를 다시 입력해 주세요"
                                autoComplete="new-password"
                                className={
                                    errors.passwordConfirm
                                        ? 'signup-input-error'
                                        : ''
                                }
                            />

                            <p className="signup-error-message">
                                {errors.passwordConfirm}
                            </p>
                        </div>

                        <button
                            className="signup-submit-button"
                            type="submit"
                        >
                            다음
                        </button>
                    </form>
                )}

                {signupStep === 2 && (
                    <div className="signup-step-panel">
                        <div className="signup-tab-box">
                            <button type="button" className="signup-tab active">
                                관심팀 선택
                            </button>

                            <button type="button" className="signup-tab" disabled>
                                알림 설정
                            </button>
                        </div>

                                                <div className="signup-team-board">
                            {isTeamsLoading && (
                                <p className="signup-step-help">
                                    팀 목록을 불러오는 중입니다.
                                </p>
                            )}

                            {!isTeamsLoading && teamLoadError && (
                                <p className="signup-error-message">
                                    {teamLoadError}
                                </p>
                            )}

                            {!isTeamsLoading &&
                                !teamLoadError &&
                                teams.map((team) => {
                                    const isSelected =
                                        selectedTeamIds.includes(team.teamId);

                                    return (
                                        <button
                                            key={team.teamId}
                                            type="button"
                                            className={`signup-team-row ${
                                                isSelected ? 'selected' : ''
                                            }`}
                                            onClick={() =>
                                                handleToggleTeam(team.teamId)
                                            }
                                        >
                                            <div className="signup-team-badge">
                                                {team.logoUrl ? (
                                                    <img
                                                        className="signup-team-logo"
                                                        src={team.logoUrl}
                                                        alt={`${team.displayName} 로고`}
                                                    />
                                                ) : (
                                                    team.abbreviation
                                                )}
                                            </div>

                                            <div className="signup-team-info">
                                                <strong>{team.displayName}</strong>
                                                <span>
                                                    {team.league} · {team.division}
                                                </span>
                                            </div>

                                            <div className="signup-team-check">
                                                {isSelected ? '✓' : '+'}
                                            </div>
                                        </button>
                                    );
                                })}
                        </div>

                        <p className="signup-step-help">
                            선택한 관심팀 수: {selectedTeamIds.length}
                        </p>

                        <div className="signup-step-actions">
                            <button
                                type="button"
                                className="signup-secondary-button"
                                onClick={() => setSignupStep(1)}
                            >
                                이전
                            </button>

                            <button
                                type="button"
                                className="signup-secondary-button"
                                onClick={handleSkipTeams}
                            >
                                건너뛰기
                            </button>

                            <button
                                type="button"
                                className="signup-submit-button"
                                onClick={handleTeamStepNext}
                            >
                                다음
                            </button>
                        </div>
                    </div>
                )}

                {signupStep === 3 && (
                    <div className="signup-step-panel">
                        <div className="signup-tab-box">
                            <button type="button" className="signup-tab" disabled>
                                관심팀 선택
                            </button>

                            <button type="button" className="signup-tab active">
                                알림 설정
                            </button>
                        </div>

                        <div className="signup-preference-summary">
                            <div>
                                <span>내 관심팀</span>
                                <strong>
                                    {selectedTeamIds.length === 0
                                        ? '선택 안 함'
                                        : `${selectedTeamIds.length}개 팀`}
                                </strong>
                            </div>

                        </div>

                        <div className="signup-notification-list">
                            <button
                                type="button"
                                className={`signup-notification-card main ${
                                    notificationSettings.all ? 'active' : ''
                                }`}
                                onClick={handleToggleAllNotifications}
                            >
                                <div>
                                    <strong>전체 알림 설정</strong>
                                    <span>모든 알림을 한 번에 켜거나 끌 수 있어요.</span>
                                </div>

                                <em>{notificationSettings.all ? 'ON' : 'OFF'}</em>
                            </button>

                            <button
                                type="button"
                                className={`signup-notification-card ${
                                    notificationSettings.gameStart ? 'active' : ''
                                }`}
                                onClick={() => handleToggleNotification('gameStart')}
                            >
                                <div>
                                    <strong>관심팀 경기 시작 알림</strong>
                                    <span>관심팀 경기가 시작되면 알려드려요.</span>
                                </div>

                                <em>{notificationSettings.gameStart ? 'ON' : 'OFF'}</em>
                            </button>

                            <button
                                type="button"
                                className={`signup-notification-card ${
                                    notificationSettings.surge ? 'active' : ''
                                }`}
                                onClick={() => handleToggleNotification('surge')}
                            >
                                <div>
                                    <strong>모멘텀 급상승 알림</strong>
                                    <span>흐름이 급변한 경기를 알려드려요.</span>
                                </div>

                                <em>{notificationSettings.surge ? 'ON' : 'OFF'}</em>
                            </button>

                            <button
                                type="button"
                                className={`signup-notification-card ${
                                    notificationSettings.gameSwitch ? 'active' : ''
                                }`}
                                onClick={() => handleToggleNotification('gameSwitch')}
                            >
                                <div>
                                    <strong>경기 전환 알림</strong>
                                    <span>지금보다 더 볼 만한 경기가 생기면 알려드려요.</span>
                                </div>

                                <em>{notificationSettings.gameSwitch ? 'ON' : 'OFF'}</em>
                            </button>
                        </div>

                        <div className="signup-step-actions">
                            <button
                                type="button"
                                className="signup-secondary-button"
                                onClick={() => setSignupStep(2)}
                            >
                                이전
                            </button>

                            <button
                                type="button"
                                className="signup-submit-button"
                                onClick={handleFinalSignup}
                            >
                                가입 완료
                            </button>
                        </div>
                    </div>
                )}

                {/* 
                    1단계 계정 정보 입력 화면에서만 로그인 안내를 보여준다.
                    2단계 관심팀 선택, 3단계 알림 설정에서는 회원가입 진행 중이므로 숨긴다.
                */}
                {signupStep === 1 && (
                    <p className="signup-login-guide">
                        이미 계정이 있으신가요?

                        <button
                            type="button"
                            className="signup-login-button"
                            onClick={() => navigate('/')}
                        >
                            로그인
                        </button>
                    </p>
                )}
            </section>
        </main>
    );
}

export default SignupPage;