import { Link, Route, Routes } from 'react-router-dom'
import { GameDetailPage } from './features/game-detail/pages/GameDetailPage'

/**
 * 앱의 최상위 라우팅 컴포넌트다.
 *
 * 현재 브랜치에서는 경기 상세 화면 구현이 목적이므로
 * 홈은 간단한 테스트 링크만 두고,
 * /games/:id 경로에서 GameDetailPage를 보여준다.
 */
function App() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <main style={{ padding: 32 }}>
            <h1>PULSE</h1>
            <p>경기 상세 화면 테스트</p>

            {/* 
              백엔드 로컬 테스트에서 사용하던 gameId다.
              나중에 홈 추천 보드가 붙으면 이 링크는 카드 클릭으로 대체된다.
            */}
            <Link to="/games/900001">테스트 경기 상세 보기</Link>
          </main>
        }
      />

      {/* 
        경기 상세 페이지 라우트다.
        URL의 :id 값은 GameDetailPage 안에서 useParams로 읽는다.
      */}
      <Route path="/games/:id" element={<GameDetailPage />} />
    </Routes>
  )
}

export default App