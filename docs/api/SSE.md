# SSE 계약

## 1. 이벤트

payload에는 점수·순위·결과 데이터를 싣지 않는다. 클라이언트는 신호를 받으면 현재 표시 모드를 유지한 채 관련 REST API를 재조회한다. 개인화와 스포일러 필터링은 REST 응답에서 적용한다.

| 이벤트 | payload | 클라이언트 동작 |
|---|---|---|
| `ranking_changed` | `{ sequence, generatedAt }` | 홈 랭킹 재조회 |
| `game_updated` | `{ gameId, sequence, generatedAt }` | 보고 있는 경기의 상세·흐름 재조회 |
| `notification_created` | `{ notificationId }` | 알림 목록 재조회 및 토스트 표시 |

## 2. 연결·인증·재연결

- 로그인 연결은 액세스 토큰으로 1회용 SSE 토큰을 발급받은 뒤 연결한다. 토큰은 Redis에 60초 동안 저장하고 연결 수립 시 소모한다.
- 비로그인 연결은 토큰 없이 생성하며 `ranking_changed`와 `game_updated`만 수신한다.
- 인증 연결만 `notification_created`를 수신한다.
- 서버는 25초 간격 SSE 코멘트로 하트비트를 전송하고 연결당 최대 수명은 60분으로 제한한다.
- `Last-Event-ID`는 사용하지 않는다. 연결 오류 시 새 토큰으로 재연결하고 관련 REST API를 한 번 재조회해 상태를 복구한다.
- `sequence`는 이벤트 종류별 인스턴스 내부 진단용 카운터다. 서버 재시작 시 초기화되며 클라이언트 동작 근거로 사용하지 않는다.
- 동시 연결 상한은 기본 1000이며 초과 요청은 503으로 거절한다.

