# ai-service 가이드

## API 문서

실행 중인 서비스가 생성하는 OpenAPI를 API 계약의 단일 기준으로 사용한다.

| 문서 | 경로 |
|---|---|
| Swagger UI | `/docs` |
| OpenAPI JSON | `/openapi.json` |

저장 조건, `contextHash` 검증과 스포일러 정책은 [AI_COPY.md](../policy/AI_COPY.md)를 따른다.

## 원칙

- 추천 여부와 관전 점수는 결정하지 않는다.
- backend가 전달한 스포일러 없는 정보만 사용한다.
- 생성 실패나 검수 실패 시 대체 문구를 만들지 않고 실패 상태를 반환한다.
