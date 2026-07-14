import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],

  server: {
    proxy: {
      // 호스트 실행은 localhost, Docker Compose는 pulse-api 컨테이너로 전달한다.
      // 브라우저 기준 동일 출처가 되므로 CORS 설정 없이 개발할 수 있다.
      '/api': {
        target: process.env.VITE_DEV_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
