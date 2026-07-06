import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'

/**
 * React Router를 사용하기 위해 App 전체를 BrowserRouter로 감싼다.
 *
 * 이렇게 해야 /games/:id 같은 브라우저 URL 경로를
 * React 앱 내부 라우팅으로 처리할 수 있다.
 */
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)