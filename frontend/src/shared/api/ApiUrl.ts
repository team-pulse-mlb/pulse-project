// API 기본 주소.
// 개발 환경에서는 Vite 프록시(/api → localhost:8080)를 사용하므로 빈 문자열이면 된다.
// 배포 환경에서만 VITE_API_BASE_URL로 백엔드 주소를 지정한다.
const ApiUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export default ApiUrl;
