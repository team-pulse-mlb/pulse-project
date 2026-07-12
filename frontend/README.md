# PULSE 프론트엔드

React 19, TypeScript, Vite, Tailwind CSS v4, TanStack Query, react-router v8 기반 웹 애플리케이션이다. Vercel Root Directory는 `frontend/`로 지정한다.

## 실행

요구사항:

- Node.js LTS
- npm
- 확인 환경: Node.js 24.17.0, npm 11.13.0

VS Code의 Git Bash 터미널에서 실행한다.

```bash
cd frontend
npm install
npm run dev
```

- 접속 주소: `http://localhost:5173`
- 백엔드가 없어도 화면 셸은 열린다. API 영역은 스켈레톤이나 빈 상태로 표시될 수 있다.
- 개발 환경은 `/api` 요청을 `http://localhost:8080`으로 프록시한다.
- 배포 환경에서만 `VITE_API_BASE_URL`을 설정한다.

검사도 `frontend/`의 Git Bash 터미널에서 실행한다.

```bash
npm run lint
npm run build
npm run preview
```

## 폴더 구조

```text
src/
├── app/                    # 라우터·레이아웃·프로바이더
├── features/
│   ├── auth/               # 회원·인증·관심 설정
│   ├── game-detail/        # 경기 상세·표시 모드
│   ├── home/               # 홈·추천 순위
│   └── notification/       # 알림 센터
├── shared/
│   ├── api/                # HTTP 클라이언트·API 주소
│   ├── components/         # 공통 UI
│   ├── hooks/              # SSE 등 공통 훅
│   ├── lib/                # 쿼리 키·포맷·QueryClient
│   └── styles/             # 디자인 토큰·전역 스타일
└── main.tsx                # 진입점
```

- `app`: 라우터·레이아웃·프로바이더만 둔다.
- `features/<기능>`: 화면 단위 코드를 `api`, `components`, `hooks`, `pages`로 나눈다.
- `shared`: 여러 기능이 함께 쓰는 코드만 둔다.

## 기능별 개발 진입점

| 기능 | 먼저 볼 파일 | 데이터·공통 코드 |
|---|---|---|
| 앱 시작·라우팅 | `src/main.tsx`, `src/app/router/root.tsx` | `src/app/providers.tsx` |
| 홈 | `src/features/home/pages/HomePage.tsx` | `hooks/useHomeQueries.ts`, `api/homeApi.ts`, `api/mappers.ts` |
| 경기 상세·펄스 그래프 | `src/features/game-detail/pages/GameDetailPage.tsx` | `components/TensionCurve.tsx`, `components/EventTimeline.tsx` |
| [구현 예정] 경기 상세 API 연결 | `features/game-detail` 담당 | `api/gameDetailApi.ts`, `hooks/useGameDetail.ts` 추가 필요 |
| SSE | `src/shared/hooks/useSse.ts` | `src/shared/lib/queryKeys.ts` |
| HTTP 요청 | 기능별 `api/*.ts` | `src/shared/api/httpClient.ts`, `src/shared/api/ApiUrl.ts` |
| [구현 예정] 알림 SSE | `features/notification` 담당 | 인증 사용자 범위의 `notification_created` 연결 필요 |

민석이 펄스 그래프를 수정할 때는 경기 상세 페이지에서 시작해 상세 API 타입과 매퍼를 함께 확인한다. 윤호가 SSE를 확장할 때는 `useSse.ts`와 `queryKeys.ts`의 키가 실제 기능 쿼리 키와 일치하는지 먼저 확인한다.

## 담당 경계

| 경로 | 담당 |
|---|---|
| `features/home`, `shared`, `app` | 예은 |
| `features/game-detail` | 민석 |
| `features/auth`, `features/notification` | 윤호 |
| `features/ai-copy` | 창현 |

`shared` 변경은 쓰기 소유자인 예은과 먼저 협의한다.

## 라우팅

- 라우트는 `src/app/router/root.tsx` 한 곳에만 등록한다.
- `react-router` v8만 사용한다. `react-router-dom`은 설치·import하지 않는다.

| 경로 | 화면 | 담당 | 로그인 |
|---|---|---|---|
| `/` | 홈 | 예은 | 불필요 |
| `/games/:gameId` | 경기 상세 | 민석 | 불필요 |
| `/signup`, `/login` | 회원가입·로그인 | 윤호 | 불필요 |
| `/onboarding`, `/mypage` | 온보딩·마이페이지 | 윤호 | 필요 |
| `/settings/teams`, `/settings/players` | 관심 설정 | 윤호 | 필요 |
| `/notifications` | 알림 센터 | 윤호 | 필요 |

## 스타일

- `src/shared/styles/global.css`의 `@theme` 토큰만 사용한다.
- 임의 hex, 컴포넌트별 `:root`, `html`·`body` 전역 오버라이드를 추가하지 않는다.
- 새 토큰은 예은에게 요청한다.
- `global.css`의 회원가입 legacy 규칙은 새 화면 기준으로 사용하지 않는다.

## 공통 컴포넌트

| 구분 | 컴포넌트 |
|---|---|
| 전역 | `Header`, `Logo`, `ToastHost`, `showToast` |
| 상태·입력 | `StatusBadge`, `SegmentToggle`, `ToggleSwitch` |
| 레이아웃 | `Card`, `InfoRow`, `SectionHeader` |
| 경기 | `GameCard`, `HeroScoreboard`, `BoxScoreTable`, `TimelineItem` |
| 상태 표시 | `Skeleton`, `EmptyState`, `Reveal` |

- 상태 배지 색을 직접 정의하지 않는다.
- 보호 모드 `HeroScoreboard`에는 점수를 전달하지 않는다.
- 토스트에는 서버가 반환한 `message` 문자열만 표시한다.

## 데이터

- API 호출은 `shared/api/httpClient.ts`만 사용한다. `fetch` 직접 호출과 토큰 재발급 재구현은 금지한다.
- 서버 상태는 TanStack Query와 `shared/lib/queryKeys.ts`로 관리한다.
- API 응답 타입과 화면 타입을 분리한다. `features/home/api/types.ts`, `mappers.ts`를 참고한다.
- SSE 신호 수신 시 `useSse`가 관련 쿼리를 invalidate한다. 개별 폴링은 추가하지 않는다.

### 홈 데이터 흐름

```text
HomePage
  → useLiveRankings / useGames
  → homeApi
  → httpClient
  → GET /api/rankings/live, GET /api/games
  → mappers
  → GameCard 표시
```

`HomePage`가 `useSse`를 한 번 연결한다. `ranking_changed`를 받으면 랭킹과 경기 목록 쿼리를 무효화하고, `game_updated`를 받으면 해당 경기 상세·이벤트 쿼리를 무효화한다. 이벤트 payload 자체로 화면 상태를 덮어쓰지 않고 TanStack Query가 서버를 다시 조회한다.

### API 주소

- 로컬 개발: 기본값을 비우면 Vite의 `/api` 프록시를 사용한다. `.env.example`처럼 `http://localhost:8080`을 직접 지정해도 된다.
- 배포: `VITE_API_BASE_URL=https://api.example.com`처럼 백엔드 origin을 지정한다.
- 예시 값은 `.env.example`에만 두고 실제 환경별 값과 시크릿은 커밋하지 않는다.

## 스포일러 하드 룰

- 보호 모드: 점수·승패·이닝 초/말·현재 타자/투수·play 원문을 표시하지 않는다.
- 긴장 곡선: 종료 경기에만 표시한다.
- 상세 화면: 태그 칩을 표시하지 않는다.
- 알림 배지: 개수를 표시하지 않는다.
- 문구: 서버 문자열만 표시하고 태그로 새 문구를 만들지 않는다.

세부 기준은 [스포일러 정책](../docs/design/SPOILER_POLICY.md)을 따른다.

## 금지 항목

- 새 라우터 생성
- `react-router-dom` 설치·import
- `fetch` 직접 호출
- `httpClient` 재작성
- 임의 hex·전역 CSS 추가
- 팀 합의 없는 의존성 추가
- `main` 직접 push
