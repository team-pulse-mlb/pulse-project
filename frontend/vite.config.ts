import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],

  server: {
    proxy: {
      /**
       * 개발 환경에서 /api로 시작하는 요청을 백엔드 서버로 전달한다.
       *
       * 브라우저는 http://localhost:5173/api/... 로 요청한다고 생각하므로
       * CORS 문제가 발생하지 않는다.
       * 실제 요청은 Vite 개발 서버가 http://localhost:8080/api/... 로 대신 전달한다.
       */
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
