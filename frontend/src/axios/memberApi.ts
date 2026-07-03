import axios from 'axios';
import ApiURL from './ApiURL';

export interface SignupRequest {
    email: string;
    password: string;
}

export interface SignupResponse {
    result?: number;
    message?: string;
}

const signupURL = `${ApiURL}/api/members/signup`;

export const signupMember = async (
    memberData: SignupRequest,
): Promise<SignupResponse> => {
    const response = await axios.post<SignupResponse>(
        signupURL,
        memberData,
    );

    return response.data;
};