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
    withdrawMember,
} from '../api/accountApi';

import '../styles/loginModal.css';

interface WithdrawMemberModalProps {
    /*
     * 모달 표시 여부입니다.
     */
    isOpen: boolean;

    /*
     * 현재 탈퇴하려는 계정의 이메일입니다.
     *
     * 사용자가 다른 계정을 실수로 탈퇴하지 않도록
     * 읽기 전용으로 표시합니다.
     */
    email: string;

    /*
     * 모달 닫기 함수입니다.
     */
    onClose: () => void;

    /*
     * 회원탈퇴 성공 후 실행할 함수입니다.
     *
     * Access Token 삭제와 홈 이동은
     * MyPage에서 처리합니다.
     */
    onSuccess: () => void;
}

interface ApiErrorResponse {
    code?: string;
    message?: string;
}

/**
 * 백엔드 오류 응답의 message를 추출합니다.
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

function WithdrawMemberModal({
    isOpen,
    email,
    onClose,
    onSuccess,
}: WithdrawMemberModalProps) {
    const [currentPassword, setCurrentPassword] =
        useState('');

    const [confirmation, setConfirmation] =
        useState('');

    const [errorMessage, setErrorMessage] =
        useState('');

    const [isWithdrawing, setIsWithdrawing] =
        useState(false);

    /*
     * 모달 안에서 마우스를 누른 뒤 바깥에서 놓았을 때
     * 실수로 닫히는 것을 막기 위한 값입니다.
     */
    const isBackdropMouseDown = useRef(false);

    /*
     * 모달을 열 때마다 이전 입력값과 오류를 초기화합니다.
     */
    useEffect(() => {
        if (!isOpen) {
            return;
        }

        setCurrentPassword('');
        setConfirmation('');
        setErrorMessage('');
        setIsWithdrawing(false);
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
                && !isWithdrawing
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
        isWithdrawing,
        onClose,
    ]);

    if (!isOpen) {
        return null;
    }

    /*
     * 백엔드는 앞뒤 공백을 제거한 뒤
     * 정확히 "회원탈퇴"인지 검사합니다.
     */
    const normalizedConfirmation =
        confirmation.trim();

    const isConfirmationMatched =
        normalizedConfirmation === '회원탈퇴';

    /**
     * 회원탈퇴 요청을 실행합니다.
     */
    const handleWithdraw:
        FormEventHandler<HTMLFormElement> =
        async (event) => {
            event.preventDefault();

            if (isWithdrawing) {
                return;
            }

            setErrorMessage('');

            /*
             * 비밀번호 값 자체는 trim하지 않습니다.
             * 실제 비밀번호에 공백이 포함될 가능성을
             * 임의로 제거하면 안 되기 때문입니다.
             */
            if (currentPassword.length === 0) {
                setErrorMessage(
                    '현재 비밀번호를 입력해 주세요.',
                );

                return;
            }

            if (normalizedConfirmation === '') {
                setErrorMessage(
                    '회원탈퇴 확인 문구를 입력해 주세요.',
                );

                return;
            }

            if (!isConfirmationMatched) {
                setErrorMessage(
                    "확인란에 '회원탈퇴'를 정확히 입력해 주세요.",
                );

                return;
            }

            setIsWithdrawing(true);

            try {
                await withdrawMember({
                    currentPassword,
                    confirmation:
                        normalizedConfirmation,
                });

                /*
                 * 민감한 비밀번호 문자열을
                 * 가능한 한 빨리 상태에서 제거합니다.
                 */
                setCurrentPassword('');
                setConfirmation('');

                onSuccess();
            } catch (error) {
                console.error(
                    '회원탈퇴 오류:',
                    error,
                );

                setErrorMessage(
                    getApiErrorMessage(
                        error,
                        '회원탈퇴를 처리하지 못했습니다.',
                    ),
                );
            } finally {
                setIsWithdrawing(false);
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
            !isWithdrawing
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
                aria-labelledby="withdraw-member-modal-title"
                onClick={(event) =>
                    event.stopPropagation()
                }
            >
                <button
                    type="button"
                    className="login-modal-close"
                    onClick={onClose}
                    disabled={isWithdrawing}
                    aria-label="회원탈퇴 모달 닫기"
                >
                    ×
                </button>

                <div className="login-modal-header">
                    <h2 id="withdraw-member-modal-title">
                        회원탈퇴
                    </h2>

                    <p>
                        탈퇴하면 현재 계정으로 다시
                        로그인할 수 없습니다.
                    </p>
                </div>

                <form
                    className="login-modal-form"
                    onSubmit={handleWithdraw}
                    noValidate
                >
                    <div className="login-modal-field">
                        <label htmlFor="withdraw-member-email">
                            탈퇴 계정
                        </label>

                        <input
                            id="withdraw-member-email"
                            type="email"
                            value={email}
                            readOnly
                            autoComplete="email"
                        />
                    </div>

                    <div className="login-modal-field">
                        <label htmlFor="withdraw-current-password">
                            현재 비밀번호
                        </label>

                        <input
                            id="withdraw-current-password"
                            type="password"
                            value={currentPassword}
                            placeholder="현재 비밀번호 입력"
                            onChange={(event) =>
                                setCurrentPassword(
                                    event.target.value,
                                )
                            }
                            autoComplete="current-password"
                            disabled={isWithdrawing}
                        />
                    </div>

                    <div className="login-modal-field">
                        <label htmlFor="withdraw-confirmation">
                            확인 문구
                        </label>

                        <input
                            id="withdraw-confirmation"
                            type="text"
                            value={confirmation}
                            placeholder="회원탈퇴 입력"
                            onChange={(event) =>
                                setConfirmation(
                                    event.target.value,
                                )
                            }
                            autoComplete="off"
                            disabled={isWithdrawing}
                        />

                        <p className="login-modal-error">
                            계속하려면
                            {' '}
                            <strong>회원탈퇴</strong>
                            {' '}
                            를 정확히 입력해 주세요.
                        </p>
                    </div>

                    <p className="login-modal-login-error">
                        {errorMessage}
                    </p>

                    <button
                        type="submit"
                        className="login-modal-submit"
                        disabled={
                            isWithdrawing
                            || !isConfirmationMatched
                        }
                    >
                        {isWithdrawing
                            ? '회원탈퇴 처리 중...'
                            : '회원탈퇴'}
                    </button>
                </form>
            </div>
        </div>
    );
}

export default WithdrawMemberModal;