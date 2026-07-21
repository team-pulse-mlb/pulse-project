# 모듈 인터페이스

| 인터페이스 | 제공 → 사용 | 계약 |
|---|---|---|
| domain 읽기 | 예은 → 전원 | JPA 엔티티 읽기 전용. 스키마 변경은 예은 담당 |
| 종료 헤드라인 조회 | 예은·민석 공통 | `games.final_headline_protected`·`final_headline_revealed` 직접 조회. 없으면 `headline=null` |
| 보호 이벤트 문구 조회 | 예은 → 민석 | `game_events.copy_protected` 직접 조회. 보호 모드 이벤트 API가 단일 원천 |
| 최근 플레이 번역 조회 | 예은 → 민석 | `plays.text_ko` 우선, 없으면 `plays.text` 폴백. 공개 모드 최근 플레이 API가 단일 원천 |
| `UserPreferenceReader` | 윤호 → 예은·api | 이메일로 관심 팀 ID, 관심 선수 ID, 알림 설정 조회. 홈 가산은 팀 일치 +10, 선수 일치 +5를 조건별 한 번만 반영 |
| SSE 재조회 신호 | api 공통 | 별도 발행 인터페이스 없이 Redis pub/sub `signal:*` 채널로 발행하고, api의 `RedisSignalRelay`가 SSE 이벤트 3종으로 중계 |
| AI 생성 트리거 | 창현 → 예은 | `FINAL_HEADLINE`·`EVENT_COPY`·`PLAY_TRANSLATION` 비동기 요청. ai-service 호출·검수·저장 담당 |
| `AiCopyContextReader` | 예은 → 창현 | 컨텍스트와 예은 측 정규화 SHA-256 `contextHash` 반환. 빈 값은 생성 대상 아님. 외부 REST 컨텍스트 API는 두지 않음 |
| `notify.events` | game processor·poller → 윤호 | 서버가 고정 템플릿으로 완성한 `message` 전달 |

`AiCopyContextReader`의 메서드와 컨텍스트 상세는 [AI_COPY.md](../policy/AI_COPY.md)를 따른다.
