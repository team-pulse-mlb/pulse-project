import {
    type ChangeEvent,
    type FormEvent,
    useState,
} from 'react';

import '../styles/signup.css';
import { signupMember } from '../axios/memberApi';


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
    const handleSendCode = () => {
        const email = formData.email.trim();

        if (email === '') {
            setErrors((prev) => ({
                ...prev,
                email: '이메일을 입력해 주세요.',
            }));
            return;
        }

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

        /*
         * 나중에 이 위치에서 이메일 인증번호 발송 API 호출
         *
         * await sendVerificationCode(email);
         */

        setIsCodeSent(true);
    };

    // 인증번호 확인 버튼
    const handleVerifyCode = () => {
        if (!/^\d{6}$/.test(formData.verificationCode)) {
            setErrors((prev) => ({
                ...prev,
                verificationCode: '인증번호 6자리를 입력해 주세요.',
            }));
            return;
        }

        /*
         * 나중에 이 위치에서 인증번호 확인 API 호출
         *
         * await verifyEmailCode({
         *     email: formData.email,
         *     code: formData.verificationCode,
         * });
         */

        console.log('입력한 인증번호:', formData.verificationCode);
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
            alert('회원가입 처리 중 오류가 발생했습니다.');
        }
    };

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
                                className={
                                    errors.email
                                        ? 'signup-input-error'
                                        : ''
                                }
                            />

                            <button
                                type="button"
                                className="email-action-button"
                                onClick={handleSendCode}
                            >
                                인증번호 받기
                            </button>
                        </div>

                        <p className="signup-error-message">
                            {errors.email}
                        </p>
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
                                >
                                    인증 확인
                                </button>
                            </div>

                            <p className="signup-error-message">
                                {errors.verificationCode}
                            </p>
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