import axios from 'axios';
import ApiUrl from '../../../shared/api/ApiUrl';

export interface SignupRequest {
    email: string;
    password: string;
}

export interface SignupResponse {
    code?: string;
    message?: string;
}

export interface EmailCheckResponse {
    code: string;
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
        `${ApiUrl}/api/members/email/check`,
        {
            params: {
                email,
            },
        }
    );

    return response.data;
};

export interface EmailCodeSendResponse {
    code: string;
    message: string;
}

export interface EmailCodeVerifyResponse {
    code: string;
    verified: boolean;
    message: string;
}

// 이메일 인증번호 발급
export const sendEmailCode = async (
    email: string,
): Promise<EmailCodeSendResponse> => {
    const response = await axios.post<EmailCodeSendResponse>(
        `${ApiUrl}/api/members/email/code/send`,
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
        `${ApiUrl}/api/members/email/code/verify`,
        {
            email,
            code,
        },
    );

    return response.data;
};
