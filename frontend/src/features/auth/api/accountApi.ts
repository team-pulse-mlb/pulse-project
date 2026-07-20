import apiClient from '../../../shared/api/httpClient';

/**
 * 계정 관련 API가 공통으로 반환하는 응답입니다.
 *
 * 예:
 * {
 *   "code": "SUCCESS",
 *   "message": "처리가 완료되었습니다."
 * }
 */
export interface AccountActionResponse {
    code: string;
    message: string;
}

/**
 * 비밀번호 변경용 이메일 인증번호 확인 요청입니다.
 *
 * 로그인한 사용자의 이메일은 JWT에서 확인하므로
 * 프론트가 이메일을 따로 보내지 않습니다.
 */
export interface PasswordChangeEmailCodeVerifyRequest {
    code: string;
}

/**
 * 비밀번호 변경용 이메일 인증번호 확인 응답입니다.
 */
export interface PasswordChangeEmailCodeVerifyResponse
    extends AccountActionResponse {
    verified: boolean;
}

/**
 * 비밀번호 변경 요청입니다.
 */
export interface ChangePasswordRequest {
    currentPassword: string;
    newPassword: string;
    newPasswordConfirm: string;
}

/**
 * 회원탈퇴 요청입니다.
 *
 * confirmation에는 백엔드 정책에 따라
 * 정확히 "회원탈퇴"를 전달해야 합니다.
 */
export interface WithdrawMemberRequest {
    currentPassword: string;
    confirmation: string;
}

/**
 * 로그인한 사용자의 이메일로
 * 비밀번호 변경용 인증번호를 발송합니다.
 *
 * 이메일 주소는 요청 본문으로 받지 않고,
 * Access Token의 사용자 정보를 기준으로 처리합니다.
 */
export const sendPasswordChangeEmailCode =
    async (): Promise<AccountActionResponse> => {
        const response =
            await apiClient.post<AccountActionResponse>(
                '/api/members/me/password/email-code/send',
            );

        return response.data;
    };

/**
 * 비밀번호 변경용 이메일 인증번호를 확인합니다.
 */
export const verifyPasswordChangeEmailCode = async (
    request: PasswordChangeEmailCodeVerifyRequest,
): Promise<PasswordChangeEmailCodeVerifyResponse> => {
    const response =
        await apiClient.post<PasswordChangeEmailCodeVerifyResponse>(
            '/api/members/me/password/email-code/verify',
            request,
        );

    return response.data;
};

/**
 * 로그인한 사용자의 비밀번호를 변경합니다.
 *
 * 성공하면 백엔드에서:
 * - 활성 Refresh Token 전체 폐기
 * - 현재 Refresh Token 쿠키 만료
 *
 * 프론트에서는 성공 후 accessToken을 삭제하고
 * 다시 로그인하도록 이동시켜야 합니다.
 */
export const changePassword = async (
    request: ChangePasswordRequest,
): Promise<AccountActionResponse> => {
    const response =
        await apiClient.put<AccountActionResponse>(
            '/api/members/me/password',
            request,
        );

    return response.data;
};

/**
 * 로그인한 사용자를 탈퇴 처리합니다.
 *
 * 성공하면 백엔드에서:
 * - 회원 상태를 WITHDRAWN으로 변경
 * - 탈퇴 시각 기록
 * - 활성 Refresh Token 전체 폐기
 * - 현재 Refresh Token 쿠키 만료
 *
 * 프론트에서는 성공 후 accessToken을 삭제해야 합니다.
 */
export const withdrawMember = async (
    request: WithdrawMemberRequest,
): Promise<AccountActionResponse> => {
    const response =
        await apiClient.post<AccountActionResponse>(
            '/api/members/me/withdraw',
            request,
        );

    return response.data;
};