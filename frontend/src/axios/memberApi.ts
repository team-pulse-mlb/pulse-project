import axios from 'axios';
import ApiUrl from './ApiUrl';

export interface SignupRequest {
    email: string;
    password: string;
}

export interface SignupResponse {
    result?: number;
    message?: string;
}

export interface EmailCheckResponse {
    available: boolean;
    message: string;
}

const signupURL = `${ApiUrl}/api/members/signup`;

export const signupMember = async (
    memberData: SignupRequest,
): Promise<SignupResponse> => {
    const response = await axios.post<SignupResponse>(
        signupURL,
        memberData,
    );

    return response.data;
};

export const checkEmailDuplicate = async (
    email: string
): Promise<EmailCheckResponse> => {
    const response = await axios.get<EmailCheckResponse>(
        `${ApiUrl}/api/members/check-email`,
        {
            params: {
                email,
            },
        }
    );

    return response.data;
};

export interface EmailCodeSendResponse {
    result: number;
    message: string;
}

export interface EmailCodeVerifyResponse {
    verified: boolean;
    message: string;
}

// 이메일 인증번호 발급
export const sendEmailCode = async (
    email: string,
): Promise<EmailCodeSendResponse> => {
    const response = await axios.post<EmailCodeSendResponse>(
        `${ApiUrl}/api/members/email-code/send`,
        {
            email,
        },
    );

    return response.data;
};

// 이메일 인증번호 확인
export const verifyEmailCode = async (
    email: string,
    code: string,
): Promise<EmailCodeVerifyResponse> => {
    const response = await axios.post<EmailCodeVerifyResponse>(
        `${ApiUrl}/api/members/email-code/verify`,
        {
            email,
            code,
        },
    );

    return response.data;
};
