# frontend

React + Vite. Vercel에 배포하며, Vercel 프로젝트 설정에서 Root Directory를 `frontend/`로 지정한다.

## 초기 생성 (최초 1회)

```bash
npm create vite@latest . -- --template react-ts
npm install
```

## 폴더 구조

```
src/
├── api/          # API 클라이언트 · SSE 훅 (useEventSource 등)
├── components/   # 경기 카드 · 추천 보드 등 공용 컴포넌트
├── pages/        # 홈 · 경기 상세 · 설정 · 알림함
└── styles/
```

## 규칙

- 외부 API(balldontlie)를 직접 호출하지 않는다. 모든 데이터는 `pulse-api`를 통해서만 받는다.
- 스포일러 보호는 서버 응답이 강제한다. 프론트는 `mode=protected` 응답을 그대로 렌더링하며, 보호 모드에서 점수·팀 우세를 추측할 수 있는 UI를 추가하지 않는다.
- 실시간 랭킹은 SSE로 수신한다.
