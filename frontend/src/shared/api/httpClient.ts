<<<<<<< HEAD
/**
 * 프론트엔드에서 백엔드 API를 호출할 때 사용할 기본 주소다.
 *
 * 개발 환경에서는 Vite proxy를 사용하기 위해 기본값을 빈 문자열로 둔다.
 * 이렇게 하면 /api/games/... 요청이 현재 프론트 주소인 localhost:5173으로 나가고,
 * Vite가 해당 요청을 localhost:8080 백엔드로 대신 전달한다.
 *
 * 배포 환경에서 프론트와 백엔드 주소가 다르면
 * VITE_API_BASE_URL 환경변수에 백엔드 주소를 넣으면 된다.
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

/**
 * fetch 응답을 JSON으로 변환하기 전에 HTTP 상태 코드를 확인한다.
 *
 * fetch는 404, 500 같은 응답도 네트워크 오류로 보지 않기 때문에
 * response.ok를 직접 확인해야 한다.
 */
export async function parseJsonResponse<T>(response: Response): Promise<T> {
    if (!response.ok) {
        throw new Error(`API 요청 실패: ${response.status}`)
    }

    return response.json() as Promise<T>
}
=======
// axios 공통 인스턴스
// - 요청 시 accessToken을 Authorization 헤더에 자동 추가
// - 401 응답 발생 시 refreshToken 쿠키로 accessToken 재발급
// - 재발급 성공 시 원래 요청을 한 번 더 재시도
// - Refresh Token Rotation 때문에 refresh 요청은 동시에 1번만 보내도록 처리

import axios, { AxiosError } from 'axios';

import ApiUrl from './ApiUrl';

interface TokenRefreshResponse {
    code: string;
    message: string;
    accessToken: string;
}

// 원래 API 요청에 사용하는 axios 인스턴스
const apiClient = axios.create({
    baseURL: ApiUrl,

    // refreshToken이 HttpOnly Cookie에 있으므로 쿠키 전송을 허용한다.
    withCredentials: true,
});

// refresh 전용 axios 인스턴스
// apiClient를 그대로 쓰면 request/response interceptor가 다시 적용되어 흐름이 꼬일 수 있다.
const refreshClient = axios.create({
    baseURL: ApiUrl,
    withCredentials: true,
});

// 혹시 전역 axios 설정에 Authorization이 들어가 있어도
// refresh 요청에는 accessToken을 보내지 않도록 방어한다.
delete refreshClient.defaults.headers.common.Authorization;

// 현재 refresh 요청이 진행 중인지 저장하는 Promise
// null이면 refresh 진행 중이 아님
let refreshPromise: Promise<string> | null = null;

/**
 * refreshToken 쿠키를 이용해 새 accessToken 발급
 *
 * 중요한 점:
 * - Refresh Token Rotation에서는 refresh 요청이 성공할 때마다 refreshToken이 교체된다.
 * - 그래서 동시에 refresh 요청이 여러 개 나가면 한 요청만 성공하고 나머지는 401이 날 수 있다.
 * - 이를 막기 위해 refreshPromise를 공유해서 refresh 요청을 한 번만 보낸다.
 */
const refreshAccessToken = async (): Promise<string> => {
    if (!refreshPromise) {
        refreshPromise = refreshClient
            .post<TokenRefreshResponse>('/api/members/refresh')
            .then((response) => {
                const newAccessToken = response.data.accessToken;

                // 새 accessToken 저장
                localStorage.setItem('accessToken', newAccessToken);

                // Header 등 로그인 상태를 사용하는 컴포넌트에게 상태 변경 알림
                window.dispatchEvent(new Event('auth-changed'));

                return newAccessToken;
            })
            .catch((error) => {
                // refresh 자체가 실패하면 로그인 상태를 정리한다.
                localStorage.removeItem('accessToken');

                window.dispatchEvent(new Event('auth-changed'));

                throw error;
            })
            .finally(() => {
                // refresh 완료 후 다음 401에서 다시 refresh할 수 있도록 초기화
                refreshPromise = null;
            });
    }

    // 이미 refresh 요청이 진행 중이면 새 요청을 보내지 않고 기존 Promise를 같이 기다린다.
    return refreshPromise;
};

apiClient.interceptors.request.use((config) => {
    const accessToken = localStorage.getItem('accessToken');

    // refresh 요청에는 accessToken을 붙이지 않는다.
    // 현재 refresh는 refreshClient가 담당하지만, 방어용으로 남겨둔다.
    const isRefreshRequest = config.url?.includes('/api/members/refresh');

    if (accessToken && !isRefreshRequest) {
        config.headers.Authorization = `Bearer ${accessToken}`;
    }

    return config;
});

apiClient.interceptors.response.use(
    (response) => response,

    async (error: AxiosError) => {
        const originalRequest = error.config as any;

        // 401이 아니면 refresh할 필요 없음
        if (error.response?.status !== 401 || !originalRequest) {
            return Promise.reject(error);
        }

        // 이미 재시도한 요청이면 무한 반복을 막기 위해 실패 처리
        if (originalRequest._retry) {
            return Promise.reject(error);
        }

        // refresh 요청 자체가 401이면 다시 refresh하지 않는다.
        const isRefreshRequest = originalRequest.url?.includes(
            '/api/members/refresh',
        );

        if (isRefreshRequest) {
            return Promise.reject(error);
        }

        originalRequest._retry = true;

        try {
            // refresh 요청은 동시에 1번만 실행된다.
            const newAccessToken = await refreshAccessToken();

            // 원래 실패했던 요청에 새 accessToken을 붙인다.
            originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;

            // 원래 요청을 한 번 더 재시도한다.
            return apiClient(originalRequest);
        } catch (refreshError) {
            localStorage.removeItem('accessToken');

            window.dispatchEvent(new Event('auth-changed'));

            return Promise.reject(refreshError);
        }
    },
);

export default apiClient;
>>>>>>> 119fe23499f2f79752f1a000ce522435b731b4ba
