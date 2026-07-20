import axios from 'axios';

import {
    useEffect,
    useRef,
    useState,
} from 'react';

import type {
    FormEventHandler,
    MouseEvent,
} from 'react';

import {
    changePassword,
    sendPasswordChangeEmailCode,
    verifyPasswordChangeEmailCode,
} from '../api/accountApi';

/*
 * 기존 로그인 모달 스타일을 재사용합니다.
 *
 * 현재 로그인 모달과 계정 설정 모달의 기본 구조가 같으므로
 * 별도 CSS를 복사하지 않고 공통으로 사용합니다.
 */
import '../styles/loginModal.css';

interface PasswordChangeModalProps {
    /*
     * 모달 표시 여부입니다.
     */
    isOpen: boolean;

    /*
     * 현재 로그인한 사용자의 이메일입니다.
     *
     * 이메일 인증번호가 이 주소로 발송된다는 것을
     * 사용자에게 읽기 전용으로 보여줍니다.
     */
    email: string;

    /*
     * 모달을 닫을 때 실행할 함수입니다.
     */
    onClose: () => void;

    /*
     * 비밀번호 변경에 성공했을 때 실행할 함수입니다.
     *
     * Access Token 삭제와 화면 이동은
     * 마이페이지에서 처리하도록 역할을 분리합니다.
     */
    onSuccess: () => void;
}

type PasswordChangeStep =
    | 'EMAIL_VERIFICATION'
    | 'PASSWORD_CHANGE';

interface ApiErrorResponse {
    code?: string;
    message?: string;
}

/**
 * Axios 오류 응답에서 백엔드의 message를 추출합니다.
 *
 * 백엔드 응답에 message가 없거나
 * Axios 외의 오류가 발생한 경우에는 fallbackMessage를 반환합니다.
 */
const getApiErrorMessage = (
    error: unknown,
    fallbackMessage: string,
): string => {
    if (
        axios.isAxiosError<ApiErrorResponse>(
            error,
        )
    ) {
        const message =
            error.response?.data?.message;

        if (
            typeof message === 'string'
            && message.trim() !== ''
        ) {
            return message;
        }
    }

    return fallbackMessage;
};

function PasswordChangeModal({
    isOpen,
    email,
    onClose,
    onSuccess,
}: PasswordChangeModalProps) {
    /*
     * 현재 모달 진행 단계입니다.
     *
     * EMAIL_VERIFICATION:
     * - 이메일 인증번호 발송 및 확인
     *
     * PASSWORD_CHANGE:
     * - 현재 비밀번호와 새 비밀번호 입력
     */
    const [step, setStep] =
        useState<PasswordChangeStep>(
            'EMAIL_VERIFICATION',
        );

    const [verificationCode, setVerificationCode] =
        useState('');

    const [currentPassword, setCurrentPassword] =
        useState('');

    const [newPassword, setNewPassword] =
        useState('');

    const [
        newPasswordConfirm,
        setNewPasswordConfirm,
    ] = useState('');

    /*
     * 인증번호가 한 번 이상 발송됐는지 나타냅니다.
     *
     * 발송 전에는 인증번호 입력창보다
     * 발송 안내가 먼저 보이도록 사용합니다.
     */
    const [isCodeSent, setIsCodeSent] =
        useState(false);

    const [isSendingCode, setIsSendingCode] =
        useState(false);

    const [isVerifyingCode, setIsVerifyingCode] =
        useState(false);

    const [isChangingPassword, setIsChangingPassword] =
        useState(false);

    const [errorMessage, setErrorMessage] =
        useState('');

    const [successMessage, setSuccessMessage] =
        useState('');

    /*
     * 모달 내부에서 마우스를 누른 뒤 바깥에서 놓는 경우
     * 실수로 닫히는 것을 막기 위한 값입니다.
     *
     * 기존 LoginModal과 동일한 방식입니다.
     */
    const isBackdropMouseDown = useRef(false);

    const isBusy =
        isSendingCode
        || isVerifyingCode
        || isChangingPassword;

    /*
     * 모달을 새로 열 때 이전 입력값과 진행 단계를 초기화합니다.
     */
    useEffect(() => {
        if (!isOpen) {
            return;
        }

        setStep('EMAIL_VERIFICATION');
        setVerificationCode('');
        setCurrentPassword('');
        setNewPassword('');
        setNewPasswordConfirm('');

        setIsCodeSent(false);
        setIsSendingCode(false);
        setIsVerifyingCode(false);
        setIsChangingPassword(false);

        setErrorMessage('');
        setSuccessMessage('');
    }, [isOpen]);

    /*
     * 모달이 열린 동안:
     * - ESC 키로 닫기
     * - 배경 화면 스크롤 방지
     */
    useEffect(() => {
        if (!isOpen) {
            return;
        }

        const previousOverflow =
            document.body.style.overflow;

        document.body.style.overflow =
            'hidden';

        const handleKeyDown = (
            event: KeyboardEvent,
        ) => {
            if (
                event.key === 'Escape'
                && !isBusy
            ) {
                onClose();
            }
        };

        window.addEventListener(
            'keydown',
            handleKeyDown,
        );

        return () => {
            window.removeEventListener(
                'keydown',
                handleKeyDown,
            );

            document.body.style.overflow =
                previousOverflow;
        };
    }, [
        isOpen,
        isBusy,
        onClose,
    ]);

    if (!isOpen) {
        return null;
    }

    /**
     * 비밀번호 변경용 이메일 인증번호를 발송합니다.
     */
    const handleSendCode = async () => {
        if (isSendingCode) {
            return;
        }

        setIsSendingCode(true);
        setErrorMessage('');
        setSuccessMessage('');

        try {
            const response =
                await sendPasswordChangeEmailCode();

            setIsCodeSent(true);

            setSuccessMessage(
                response.message
                || '인증번호를 발송했습니다.',
            );
        } catch (error) {
            console.error(
                '비밀번호 변경 인증번호 발송 오류:',
                error,
            );

            setErrorMessage(
                getApiErrorMessage(
                    error,
                    '인증번호를 발송하지 못했습니다. 잠시 후 다시 시도해 주세요.',
                ),
            );
        } finally {
            setIsSendingCode(false);
        }
    };

    /**
     * 이메일로 받은 인증번호를 확인합니다.
     */
    const handleVerifyCode:
        FormEventHandler<HTMLFormElement> =
        async (event) => {
            event.preventDefault();

            if (isVerifyingCode) {
                return;
            }

            setErrorMessage('');
            setSuccessMessage('');

            const normalizedCode =
                verificationCode.trim();

            if (normalizedCode === '') {
                setErrorMessage(
                    '이메일로 받은 인증번호를 입력해 주세요.',
                );

                return;
            }

            if (!isCodeSent) {
                setErrorMessage(
                    '먼저 인증번호를 발송해 주세요.',
                );

                return;
            }

            setIsVerifyingCode(true);

            try {
                const response =
                    await verifyPasswordChangeEmailCode({
                        code: normalizedCode,
                    });

                if (!response.verified) {
                    setErrorMessage(
                        response.message
                        || '인증번호를 확인하지 못했습니다.',
                    );

                    return;
                }

                setStep('PASSWORD_CHANGE');
                setVerificationCode('');

                setSuccessMessage(
                    '이메일 인증이 완료되었습니다.',
                );
            } catch (error) {
                console.error(
                    '비밀번호 변경 이메일 인증 오류:',
                    error,
                );

                setErrorMessage(
                    getApiErrorMessage(
                        error,
                        '인증번호를 확인하지 못했습니다.',
                    ),
                );
            } finally {
                setIsVerifyingCode(false);
            }
        };

    /**
     * 현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다.
     */
    const handleChangePassword:
        FormEventHandler<HTMLFormElement> =
        async (event) => {
            event.preventDefault();

            if (isChangingPassword) {
                return;
            }

            setErrorMessage('');
            setSuccessMessage('');

            /*
             * 비밀번호는 실제 값에 공백이 포함될 수도 있으므로
             * trim으로 값을 변경하지 않습니다.
             */
            if (currentPassword.length === 0) {
                setErrorMessage(
                    '현재 비밀번호를 입력해 주세요.',
                );

                return;
            }

            if (newPassword.length === 0) {
                setErrorMessage(
                    '새 비밀번호를 입력해 주세요.',
                );

                return;
            }

            if (newPasswordConfirm.length === 0) {
                setErrorMessage(
                    '새 비밀번호 확인을 입력해 주세요.',
                );

                return;
            }

            if (
                newPassword
                !== newPasswordConfirm
            ) {
                setErrorMessage(
                    '새 비밀번호와 비밀번호 확인이 일치하지 않습니다.',
                );

                return;
            }

            if (
                currentPassword
                === newPassword
            ) {
                setErrorMessage(
                    '현재 비밀번호와 다른 비밀번호를 입력해 주세요.',
                );

                return;
            }

            setIsChangingPassword(true);

            try {
                await changePassword({
                    currentPassword,
                    newPassword,
                    newPasswordConfirm,
                });

                /*
                 * 비밀번호 문자열을 가능한 한 빨리
                 * React 상태에서도 제거합니다.
                 */
                setCurrentPassword('');
                setNewPassword('');
                setNewPasswordConfirm('');

                onSuccess();
            } catch (error) {
                console.error(
                    '비밀번호 변경 오류:',
                    error,
                );

                setErrorMessage(
                    getApiErrorMessage(
                        error,
                        '비밀번호를 변경하지 못했습니다.',
                    ),
                );
            } finally {
                setIsChangingPassword(false);
            }
        };

    const handleBackdropMouseDown = (
        event: MouseEvent<HTMLDivElement>,
    ) => {
        isBackdropMouseDown.current =
            event.target === event.currentTarget;
    };

    const handleBackdropClick = (
        event: MouseEvent<HTMLDivElement>,
    ) => {
        if (
            !isBusy
            && isBackdropMouseDown.current
            && event.target
                === event.currentTarget
        ) {
            onClose();
        }

        isBackdropMouseDown.current =
            false;
    };

    return (
        <div
            className="login-modal-backdrop"
            onMouseDown={
                handleBackdropMouseDown
            }
            onClick={handleBackdropClick}
        >
            <div
                className="login-modal"
                role="dialog"
                aria-modal="true"
                aria-labelledby="password-change-modal-title"
                onClick={(event) =>
                    event.stopPropagation()
                }
            >
                <button
                    type="button"
                    className="login-modal-close"
                    onClick={onClose}
                    disabled={isBusy}
                    aria-label="비밀번호 변경 모달 닫기"
                >
                    ×
                </button>

                <div className="login-modal-header">
                    <h2 id="password-change-modal-title">
                        비밀번호 변경
                    </h2>

                    <p>
                        {step === 'EMAIL_VERIFICATION'
                            ? '계정 보호를 위해 이메일 인증을 먼저 진행합니다.'
                            : '현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다.'}
                    </p>
                </div>

                {step === 'EMAIL_VERIFICATION' ? (
                    <form
                        className="login-modal-form"
                        onSubmit={handleVerifyCode}
                        noValidate
                    >
                        <div className="login-modal-field">
                            <label htmlFor="password-change-email">
                                로그인 이메일
                            </label>

                            <input
                                id="password-change-email"
                                type="email"
                                value={email}
                                readOnly
                                autoComplete="email"
                            />

                            <p className="login-modal-error">
                                {' '}
                            </p>
                        </div>

                        <button
                            type="button"
                            className="login-modal-submit"
                            onClick={handleSendCode}
                            disabled={isBusy}
                        >
                            {isSendingCode
                                ? '인증번호 발송 중...'
                                : isCodeSent
                                    ? '인증번호 다시 받기'
                                    : '인증번호 받기'}
                        </button>

                        <div className="login-modal-field">
                            <label htmlFor="password-change-code">
                                인증번호
                            </label>

                            <input
                                id="password-change-code"
                                type="text"
                                inputMode="numeric"
                                value={verificationCode}
                                placeholder="이메일로 받은 인증번호"
                                onChange={(event) =>
                                    setVerificationCode(
                                        event.target.value,
                                    )
                                }
                                autoComplete="one-time-code"
                                disabled={!isCodeSent || isBusy}
                            />
                        </div>

                        <p className="login-modal-login-error">
                            {errorMessage}
                        </p>

                        {!errorMessage && (
                            <p className="login-modal-login-error">
                                {successMessage}
                            </p>
                        )}

                        <button
                            type="submit"
                            className="login-modal-submit"
                            disabled={
                                !isCodeSent
                                || isBusy
                            }
                        >
                            {isVerifyingCode
                                ? '인증번호 확인 중...'
                                : '인증번호 확인'}
                        </button>
                    </form>
                ) : (
                    <form
                        className="login-modal-form"
                        onSubmit={handleChangePassword}
                        noValidate
                    >
                        <div className="login-modal-field">
                            <label htmlFor="current-password">
                                현재 비밀번호
                            </label>

                            <input
                                id="current-password"
                                type="password"
                                value={currentPassword}
                                placeholder="현재 비밀번호"
                                onChange={(event) =>
                                    setCurrentPassword(
                                        event.target.value,
                                    )
                                }
                                autoComplete="current-password"
                                disabled={isBusy}
                            />
                        </div>

                        <div className="login-modal-field">
                            <label htmlFor="new-password">
                                새 비밀번호
                            </label>

                            <input
                                id="new-password"
                                type="password"
                                value={newPassword}
                                placeholder="새 비밀번호"
                                onChange={(event) =>
                                    setNewPassword(
                                        event.target.value,
                                    )
                                }
                                autoComplete="new-password"
                                disabled={isBusy}
                            />
                        </div>

                        <div className="login-modal-field">
                            <label htmlFor="new-password-confirm">
                                새 비밀번호 확인
                            </label>

                            <input
                                id="new-password-confirm"
                                type="password"
                                value={newPasswordConfirm}
                                placeholder="새 비밀번호 다시 입력"
                                onChange={(event) =>
                                    setNewPasswordConfirm(
                                        event.target.value,
                                    )
                                }
                                autoComplete="new-password"
                                disabled={isBusy}
                            />
                        </div>

                        <p className="login-modal-login-error">
                            {errorMessage}
                        </p>

                        {!errorMessage && (
                            <p className="login-modal-login-error">
                                {successMessage}
                            </p>
                        )}

                        <button
                            type="submit"
                            className="login-modal-submit"
                            disabled={isBusy}
                        >
                            {isChangingPassword
                                ? '비밀번호 변경 중...'
                                : '비밀번호 변경'}
                        </button>
                    </form>
                )}
            </div>
        </div>
    );
}

export default PasswordChangeModal;