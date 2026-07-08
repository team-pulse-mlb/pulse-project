const ApiUrl = import.meta.env.VITE_API_BASE_URL;

if (!ApiUrl) {
    throw new Error('VITE_API_BASE_URL 환경변수가 설정되지 않았습니다.');
}

export default ApiUrl;
