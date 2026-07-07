// axios 공통 인스턴스
// - 요청 시 accessToken을 Authorization 헤더에 자동 추가
// - 401 응답 발생 시 refreshToken 쿠키로 accessToken 재발급
// - 재발급 성공 시 원래 요청을 한 번 더 재시도

import axios, { AxiosError } from 'axios';

import ApiUrl from './ApiUrl';

interface TokenRefreshResponse {
    result: number;
    message: string;
    accessToken: string;
}

const apiClient = axios.create({
    baseURL: ApiUrl,
    // refreshToken이 HttpOnly Cookie에 있으므로 쿠키 전송을 허용한다.
    withCredentials: true,
});

apiClient.interceptors.request.use((config) => {
    const accessToken = localStorage.getItem('accessToken');

    if (accessToken) {
        config.headers.Authorization = `Bearer ${accessToken}`;
    }

    return config;
});

apiClient.interceptors.response.use(
    (response) => response,

    async (error: AxiosError) => {
        const originalRequest = error.config as any;

        // 401이 아니거나, 이미 재시도한 요청이면 그대로 실패 처리한다.
        // _retry가 없으면 refresh 실패 시 무한 재시도될 수 있다.
        if (
        error.response?.status !== 401 ||
        !originalRequest ||
        originalRequest._retry
        ) {
        return Promise.reject(error);
        }

        originalRequest._retry = true;

        try {
            // refresh 요청은 apiClient가 아니라 일반 axios로 보낸다.
            // apiClient를 쓰면 refresh 요청 자체도 interceptor 대상이 되어 흐름이 꼬일 수 있다.
            const refreshResponse = await axios.post<TokenRefreshResponse>(
                `${ApiUrl}/api/members/refresh`,
                {},
                {
                withCredentials: true,
                },
            );

            const newAccessToken = refreshResponse.data.accessToken;

            localStorage.setItem('accessToken', newAccessToken);
            
            // Header 등 로그인 상태를 사용하는 컴포넌트에게 상태 변경을 알린다.
            window.dispatchEvent(new Event('auth-changed'));

            originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;

            return apiClient(originalRequest);
        } catch (refreshError) {
        localStorage.removeItem('accessToken');

        window.dispatchEvent(new Event('auth-changed'));

        return Promise.reject(refreshError);
        }
    },
);

export default apiClient;