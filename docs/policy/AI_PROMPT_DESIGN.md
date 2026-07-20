# AI 프롬프트 설계

## 1. 문서 범위

이 문서는 `ai-service`가 OpenAI에 전달하는 프롬프트의 조립 방식과 출력 제어만 다룬다. 생성 트리거, `safeContext` 필드, HTTP 계약, 검수·저장 조건, 화면 폴백은 [AI_COPY.md](AI_COPY.md)를 따르고, 보호·공개 모드의 노출 정책은 [SPOILER_POLICY.md](SPOILER_POLICY.md)를 따른다.

PULSE는 별도의 `system` 역할 메시지를 사용하지 않는다. OpenAI Responses API의 `input` 상단에 역할·목적·모드 정책을 명시하고, 서버가 선별한 요청 데이터를 마지막에 배치한다. 사용자의 자유 형식 입력은 프롬프트에 전달하지 않는다.

## 2. AI 문구 프롬프트 조립

`FINAL_HEADLINE`과 `EVENT_COPY`는 `spoiler_free_prompt.py`의 공통 빌더를 사용한다. 빌더는 요청 DTO의 실제 타입으로 목적을 결정하고, 다음 순서로 프롬프트를 조립한다.

1. 역할: MLB 경기 관전 타이밍 서비스의 AI 문구 생성기로 한정한다.
2. 목적 지시: 종료 경기 헤드라인과 이벤트 타임라인 문구의 역할을 분리한다.
3. 모드 정책: `PROTECTED`와 `REVEALED`에서 허용하거나 금지할 표현을 구분한다.
4. 품질 규칙: 문체·길이·핵심 흐름 선택 기준과 조건부 예시를 적용한다.
5. 공통 규칙: `safeContext` 밖의 사실 추측, 내부 추천 점수 노출, 설명·마크다운 출력을 금지한다.
6. 응답 계약: 목적에 맞는 JSON 필드와 근거 ID 작성 규칙을 지정한다.
7. 요청 데이터: `purpose`, `mode`, `language`, `maxLength`, `safeContext`를 JSON으로 제공한다.

프롬프트 빌더도 요청 목적과 모드에 맞는 필드만 다시 선별한다. 값이 없는 선택 필드는 제외하여 모델이 `null`의 의미를 추측하지 않게 한다. 원천 단계의 `safeContext` 화이트리스트와 함께 입력 범위를 이중으로 제한하는 구조다.

### 2.1 목적·모드별 지시

| 목적·모드 | 생성 지시 | 출력 근거 |
|---|---|---|
| `FINAL_HEADLINE` · `PROTECTED` | 결과를 노출하지 않고 검증된 긴장 구간이나 관전 가치를 한국어 한 문장, 최대 80자로 요약한다. 점수·승패·팀 유불리·선수·원본 play는 생성하지 않는다. | `used_fact_ids=[]`, `used_play_ids=[]` 고정 |
| `FINAL_HEADLINE` · `REVEALED` | 최종 결과와 검증된 핵심 흐름 한 가지를 우선 결합한다. 팀명·점수·선수·경기 흐름은 `summaryFacts`와 `verifiedPlays` 등 실제 컨텍스트에 있는 값만 사용한다. | 실제 문구에 사용한 fact·play ID만 선언 |
| `EVENT_COPY` · `PROTECTED` | 화면의 이닝 헤더와 중복되지 않게 이닝·회차를 문구에서 제외하고, `contributingLabels`와 `situation`의 카운트·아웃·주자 정보만 `~합니다`체 한 문장으로 조합한다. | OpenAI 출력은 `safe_title`만 사용 |

운영 계약상 `EVENT_COPY`는 `PROTECTED`만 생성한다. 공개 모드 경기 흐름은 이벤트 문구가 아니라 `PLAY_TRANSLATION` 결과를 사용한다.

### 2.2 예시 적용 원칙

예시는 모든 요청에 공통으로 붙이지 않고 목적과 모드에 필요한 경우에만 포함한다.

- 공개 헤드라인은 후반 결정, 역전, 영봉, 연장, 결정 플레이, 타격전의 문장 구조를 조건부 예시로 제공한다. 현재 `safeContext`에 같은 종류의 검증 근거가 있을 때만 구조를 참고하며, 예시의 팀명·점수·이닝·선수명을 복사하지 못하게 지시한다.
- 보호 이벤트 문구는 만루·득점권·긴 타석처럼 허용된 상황 정보만 조합하는 예시를 제공한다. 결과성 사건을 예시로 넣지 않는다.
- 예시에서 사용한 사실과 play도 실제 출력에서는 `used_fact_ids`와 `used_play_ids`에 선언해야 한다. 예시는 근거 검증을 우회하지 않는다.

## 3. 플레이 번역 프롬프트

`PLAY_TRANSLATION`은 `play_translation_prompt.py`의 별도 빌더를 사용한다. 전체 야구 용어집을 매번 전달하지 않고, 원문과 실제로 일치하는 이벤트·수식어·방향·수비 위치 규칙만 `matchedRules`에 포함한다. 관련 없는 규칙으로 인한 번역 혼선을 줄이고 동일 입력에서 동일 프롬프트가 생성되게 한다.

번역 프롬프트는 다음을 강제한다.

- 단일 `Play Result`만 한국어 중계·기록 문체의 한 문장으로 번역한다.
- 선수명, 숫자, 거리, 베이스 번호, 아웃·출루·타구 결과와 사건 순서를 보존한다.
- 매칭된 `canonicalKo`와 `requiredKo`를 우선하며, 수비 처리 위치와 타구 방향을 구분한다.
- 원문에 없는 점수, 이닝, 선수, 승패, 타점, 감정, 평가, 해설을 추가하지 않는다.
- JSON 객체의 `translated_text` 한 필드만 반환한다.

용어 매핑과 금지 표현의 단일 기준은 `ai-service/app/resources/baseball_terms_ko.yml`이다. 프롬프트 빌더와 번역 Guard는 같은 용어집을 사용한다.

## 4. Structured Output과 응답 변환

OpenAI 호출은 Responses API의 JSON Schema Structured Output을 `strict=true`로 사용하고, 정의하지 않은 추가 필드는 허용하지 않는다.

| 목적 | OpenAI 필수 필드 | 제약 |
|---|---|---|
| `FINAL_HEADLINE` | `safe_title`, `used_fact_ids`, `used_play_ids` | 문자열 1개, 문자열 배열, 양의 정수 배열 |
| `EVENT_COPY` | `safe_title` | 다른 근거 필드를 추가하지 않음 |
| `PLAY_TRANSLATION` | `translated_text` | 비어 있지 않은 문자열 |

OpenAI 내부 계약은 `snake_case`를 사용한다. FastAPI의 Pydantic 응답 모델은 외부 HTTP 계약에 맞게 `safeTitle`, `usedFactIds`, `usedPlayIds`, `translatedText` 같은 `camelCase`로 직렬화한다. 생성·파싱·검수에 실패하면 생성 문구나 번역 필드는 비우고 `violations`를 반환한다.

## 5. 생성 근거와 Guard 연계

`FINAL_HEADLINE`은 문구와 함께 실제 사용 근거를 선언한다. `used_fact_ids`에는 허용된 `summaryFacts` ID만, `used_play_ids`에는 실제 `verifiedPlays.playId`만 넣는다. 컨텍스트에 존재한다는 이유만으로 사용하지 않은 근거를 포함하지 않는다.

Evidence Guard는 다음 계약을 규칙 기반으로 검증한다.

- 존재하지 않거나 값이 `null`인 fact ID, 존재하지 않는 play ID, 중복 ID를 차단한다.
- 역전·끝내기·영봉·연장·총득점 같은 표현에 필요한 fact가 선언됐는지 확인한다.
- 결승타·동점타·쐐기타 같은 표현에 필요한 play 태그가 있는지 확인한다.
- 선수명을 사용하면 선언한 play의 batter 또는 pitcher와 연결되는지 확인한다.
- `PROTECTED` 헤드라인의 근거 배열이 비어 있는지 확인한다.

Evidence Guard를 통과한 뒤에도 Spoiler Guard와 backend의 최신 `contextHash` 검증을 통과해야 저장할 수 있다. 전체 저장 조건과 위반 코드 기준은 [AI_COPY.md](AI_COPY.md)를 따른다.

## 6. 호출 실패 제어와 로그 제한

OpenAI SDK의 자동 재시도는 비활성화한다. 서비스 계층이 목적별 시도 상한 안에서 timeout, 연결 오류, rate limit, 빈 응답, JSON 파싱 실패, 필수 필드 누락만 재시도한다. 재시도 간격은 상한이 있는 지수 백오프와 jitter로 계산한다. 재시도 대상이 아니거나 상한을 넘긴 오류는 violation으로 변환하며 대체 문구를 생성하지 않는다.

재시도 로그에는 `gameId`, 목적, 모드, 모델, 현재·최대 시도 횟수, 실패 사유만 남긴다. 프롬프트 본문, 생성 문구, `contextHash`는 로그에 기록하지 않는다.

## 7. 구현·검증 기준

| 역할 | 구현 파일 |
|---|---|
| 헤드라인·이벤트 프롬프트 | `ai-service/app/prompts/spoiler_free_prompt.py` |
| 플레이 번역 프롬프트 | `ai-service/app/prompts/play_translation_prompt.py` |
| Structured Output·재시도 | `ai-service/app/services/openai_service.py`, `play_translation_service.py` |
| 응답 스키마 | `ai-service/app/schemas/ai_schema.py` |
| 헤드라인 근거 검증 | `ai-service/app/services/final_headline_evidence_guard.py` |
| 스포일러·번역 검증 | `ai-service/app/services/spoiler_guard.py`, `play_translation_guard.py` |
| 야구 용어집 | `ai-service/app/resources/baseball_terms_ko.yml` |

프롬프트를 변경할 때는 목적·모드별 프롬프트 문자열, Structured Output 스키마, 응답 alias, 프롬프트와 Guard의 fact ID 일치, 번역 용어 매칭과 사실 보존 테스트를 함께 갱신한다. 테스트 통과 건수 같은 실행 시점의 수치는 문서에 고정하지 않는다.
