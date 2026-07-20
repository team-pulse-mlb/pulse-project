# frontend 개발·검증 가이드

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

## 배포

S3 + CloudFront로 배포한다. `main`의 `frontend/**` 변경은 `.github/workflows/frontend-deploy.yml`이 자동 배포한다. 리소스·수동 절차·캐시 정책은 [`infra/prod/FRONTEND.md`](../../infra/prod/FRONTEND.md)를 따른다.
