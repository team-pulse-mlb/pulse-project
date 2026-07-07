import axios from 'axios';

import ApiUrl from '../../../axios/ApiUrl';
import apiClient from '../../../axios/apiClient';

export interface LoginRequest {
    email: string;
    password: string;
}

export interface LoginResponse {
    result: number;
    message: string;
    accessToken: string;
}

export interface MeResponse {
    result: number;
    email: string;
    roles: string[];
}

export const login = async (
    loginData: LoginRequest,
): Promise<LoginResponse> => {
    const response = await axios.post<LoginResponse>(
        `${ApiUrl}/api/members/login`,
        loginData,
        {
        withCredentials: true,
        },
    );

    return response.data;
};

export const getMe = async (): Promise<MeResponse> => {
    const response = await apiClient.get<MeResponse>(
        '/api/members/me',
    );

    return response.data;
};

export const refreshAccessToken = async (): Promise<LoginResponse> => {
    const response = await axios.post<LoginResponse>(
        `${ApiUrl}/api/members/refresh`,
        {},
        {
        withCredentials: true,
        },
    );

    return response.data;
};

export const logout = async (): Promise<void> => {
    await axios.post(
        `${ApiUrl}/api/members/logout`,
        {},
        {
        withCredentials: true,
        },
    );
};