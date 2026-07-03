import axios from 'axios';

import {
    type ChangeEvent,
    type FormEvent,
    useState,
} from 'react';

import '../styles/signup.css';

import { 
    signupMember, 
    checkEmailDuplicate,
    sendEmailCode, 
    verifyEmailCode 
} from '../axios/memberApi';


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
    const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
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

        // Spring 서버로 보낼 데이터
        const signupRequest = {
            email,
            password: formData.password,
        };

        console.log('회원가입 요청 데이터:', signupRequest);

        try {
            const result = await signupMember(signupRequest);

            console.log('회원가입 응답:', result);
            alert(result.message ?? '회원가입 요청이 완료되었습니다.');
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
                    <h2>회원가입</h2>
                    <p>
                        이메일과 비밀번호만으로 간편하게
                        시작하세요.
                    </p>
                </header>

                <form
                    className="signup-form"
                    onSubmit={handleSubmit}
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
                        회원가입
                    </button>
                </form>

                <p className="signup-login-guide">
                    이미 계정이 있으신가요?

                    <button
                        type="button"
                        className="signup-login-button"
                    >
                        로그인
                    </button>
                </p>
            </section>
        </main>
    );
}

export default SignupPage;