# PULSE 프론트엔드

React 19·TypeScript·Vite 기반 웹 애플리케이션이다.

## 폴더 구조

```text
src/
├── app/                    라우터·레이아웃·프로바이더
├── features/
│   ├── auth/               회원·인증·관심 설정
│   ├── game-detail/        경기 상세·표시 모드
│   ├── home/               홈·추천 순위
│   └── notification/       알림 센터
├── shared/
│   ├── api/                HTTP 클라이언트
│   ├── components/         공통 UI
│   ├── hooks/              SSE 등 공통 훅
│   ├── lib/                쿼리 키·QueryClient
│   └── styles/             디자인 토큰·전역 스타일
└── main.tsx                애플리케이션 진입점
```

## 목차

| 목차 | 설명 |
|---|---|
| [실행](#실행) | 개발 서버 시작 |
| [기능별 진입점](#기능별-진입점) | 수정할 기능의 주요 파일 |
| [개발 기준](#개발-기준) | 라우팅·API·상태 관리 원칙 |
| [검증](#검증) | lint와 빌드 |

명령은 저장소 루트의 Git Bash를 기준으로 한다.

## 실행

Node.js LTS와 npm이 필요하다.

```bash
cd frontend
npm install
npm run dev
```

- 화면: `http://localhost:5173`
- API 프록시: `http://localhost:8080`
- 백엔드가 없으면 API 영역은 빈 상태로 표시될 수 있다.

## 기능별 진입점

| 기능 | 화면 | 데이터·공통 코드 |
|---|---|---|
| 앱 시작·라우팅 | `src/main.tsx`, `src/app/router/root.tsx` | `src/app/providers.tsx` |
| 홈 | `src/features/home/pages/HomePage.tsx` | `hooks/useHomeQueries.ts`, `api/homeApi.ts` |
| 경기 상세 | `src/features/game-detail/pages/GameDetailPage.tsx` | `components/TensionCurve.tsx`, `components/EventTimeline.tsx` |
| SSE | `src/shared/hooks/useSse.ts` | `src/shared/lib/queryKeys.ts` |
| HTTP 요청 | 기능별 `api/*.ts` | `src/shared/api/httpClient.ts` |
| 공통 스타일 | 기능별 컴포넌트 | `src/shared/styles/global.css` |

## 개발 기준

- 라우트는 `src/app/router/root.tsx`에 등록한다.
- API 호출은 `src/shared/api/httpClient.ts`를 사용한다.
- 서버 상태는 TanStack Query와 `queryKeys.ts`로 관리한다.
- SSE는 변경 신호를 받으면 관련 쿼리를 다시 조회한다.
- API 응답 타입과 화면 표시 타입은 분리한다.
- 공통 색상은 `global.css`의 `@theme` 토큰을 사용한다.
- 보호 모드에는 점수·승패·팀 우세 정보를 노출하지 않는다.

로컬에서는 Vite의 `/api` 프록시를 사용한다. 배포할 때만 `VITE_API_BASE_URL`을 설정한다.

## 검증

```bash
cd frontend
npm run lint
npm run build
```

빌드 결과를 직접 확인할 때만 `npm run preview`를 실행한다.
