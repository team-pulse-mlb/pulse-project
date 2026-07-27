# Redis 키 명세

| 키 | 타입 | 내용 |
|---|---|---|
| `score:rank:live` | ZSET | 진행 중 경기 랭킹. member는 game ID, score는 watch score |
| `game:{id}:live` | HASH | 현재 점수·이닝·최신 태그 내부 캐시. 종료 시 삭제하며 비상 TTL 12시간 |
| `notify:armed:{gameId}` | STRING | 급상승 히스테리시스 상태 |
| `notify:cooldown:{gameId}` | STRING | 급상승 경기별 15분 쿨다운 |
| `notify:surge:count:global` | STRING | 전역 15분 창 발화 수 |
| `game:switch:suggestion:{userId}:{gameId}` | STRING | 로그인 사용자별 경기 전환 안내 쿨다운(15분). 비로그인 사용자는 프론트 localStorage로 보조 처리 |
| `sse:token:{token}` | STRING | SSE 연결용 1회용 토큰, TTL 60초 |
| `signal:ranking` | pub/sub | 홈 랭킹 재조회 신호 |
| `signal:game:{id}` | pub/sub | 경기 상세 재조회 신호 |
| `signal:notification:{userId}` | pub/sub | 사용자 알림 재조회 신호 |

AI 문구는 Redis에 캐시하지 않고 `games`, `game_events`, `plays`에서 직접 조회한다. 경기가 라이브 상태에서 이탈하면 scorer가 `score:rank:live`에서 제거하고 랭킹 신호를 발행한다.

`game:{id}:live`는 경기 종료 정리 시 game processor가 삭제한다. 종료 ScoreTask가 유실돼 정리가 실행되지 않는 경우에 대비해 비상 TTL(기본 12시간, `pulse.game-processor.live-cache-ttl-ms`)을 함께 건다. TTL은 캐시를 갱신할 때마다 다시 걸리므로 진행 중인 경기에서는 만료되지 않고, 갱신이 끊긴 경기의 키만 소멸한다.

