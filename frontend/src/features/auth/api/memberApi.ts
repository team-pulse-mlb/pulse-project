import axios from 'axios';
import ApiUrl from '../../../shared/api/ApiUrl';

export interface SignupNotificationSettings {
    /*
     * 전체 알림 토글.
     *
     * 백엔드 DB에는 저장하지 않고,
     * 프론트 UI에서 개별 알림을 한 번에 켜고 끄는 용도로 사용한다.
     */
    all: boolean;

    /*
     * 관심팀 경기 시작 알림.
     */
    gameStart: boolean;

    /*
     * 모멘텀 급상승 알림.
     */
    surge: boolean;

    /*
     * 경기 전환 알림.
     */
    gameSwitch: boolean;
}

export interface SignupRequest {
    email: string;
    password: string;

    /*
     * 사용자가 선택한 관심팀 ID 목록.
     *
     * 백엔드 SignupRequest의 selectedTeamIds와 이름을 맞춰야 한다.
     */
    selectedTeamIds: number[];

    /*
     * 회원가입 Step 3에서 설정한 알림 값.
     *
     * 백엔드 SignupRequest.NotificationSettingsRequest와 구조를 맞춘다.
     */
    notificationSettings: SignupNotificationSettings;
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
