import axios from "axios";

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
 * axios를 사용하는 인증/회원 API용 공통 클라이언트다.
 *
 * 기존 authApi.ts가 httpClient의 default export를 apiClient로 가져오고 있으므로,
 * 병합 후 빌드 오류를 막기 위해 default export를 제공한다.
 *
 * baseURL은 fetch 기반 API와 같은 API_BASE_URL을 사용해서
 * 개발 환경에서는 Vite proxy를 그대로 타고, 배포 환경에서는 환경변수 주소를 사용한다.
 */
const apiClient = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        'Content-Type': 'application/json',
    },
})

export default apiClient

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
