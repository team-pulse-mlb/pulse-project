# AI 서비스 테스트

## 1. 범위

- 기준 경로: `ai-service/tests`
- 테스트 파일 패턴: `test_*.py`
- 실행 환경: Python 3.12
- 기준 pytest 버전: 9.1.1
- Router·Schema·Prompt·OpenAI client wrapper·스포일러·근거·번역 검수를 테스트한다.
- 실제 테스트 수는 pytest 수집 결과와 JUnit XML 결과를 함께 확인한다.
- subtest는 일반 테스트 수집 항목과 분리해 기록한다.

## 2. 사용 도구

| 도구 | 용도 |
|---|---|
| pytest | 테스트 수집·실행·결과 보고 |
| unittest.TestCase | 테스트 클래스와 생명주기 구성 |
| unittest.mock | OpenAI 호출과 협력 객체 대역·예외 시나리오 검증 |
| FastAPI TestClient | AI HTTP endpoint 요청·응답 계약 검증 |
| Pydantic | Spring camelCase 요청과 AI 응답 Schema 검증 |
| JUnit XML | CI에서 사용할 테스트 실행 결과 집계 |
| Docker | 로컬 Python 버전과 무관한 Python 3.12 테스트 환경 제공 |

## 3. 테스트 파일별 범위

| 테스트 파일 | 핵심 검증 범위 |
|---|---|
| `test_ai_router.py` | FINAL_HEADLINE·EVENT_COPY·PLAY_TRANSLATION endpoint의 성공·실패 응답, 모드 제한, evidence·translation guard 결과를 검증한다. |
| `test_ai_router_logging.py` | 생성 성공·실패·Spoiler Guard 거절 로그가 요청 메타데이터와 위반 사유를 기록하고 생성 문구 전문은 남기지 않는지 검증한다. |
| `test_ai_schema.py` | Spring camelCase payload, 모드별 safe context, evidence ID 기본값과 응답 직렬화 계약을 검증한다. |
| `test_final_headline_evidence_guard.py` | fact·play ID 존재 여부, 중복 evidence, 선수 연결, 역전·끝내기·결승타·총득점 등 사실 근거 계약을 검증한다. |
| `test_openai_service.py` | Structured Output Schema, 모델별 옵션, 목적별 timeout·재시도, 응답 파싱과 evidence 보존을 검증한다. |
| `test_play_translation_guard.py` | 선수명·숫자·타격 이벤트·방향·수비 위치·스코어·추가 해설의 누락·변조·추가를 검증한다. |
| `test_play_translation_prompt.py` | YAML 용어집 매칭, 이벤트·방향·수비 위치 우선순위, 결정적 Prompt 생성과 출력 규칙을 검증한다. |
| `test_play_translation_service.py` | 번역 Structured Output, 모델 옵션, timeout·재시도, 오류 변환과 응답 파싱을 검증한다. |
| `test_spoiler_free_prompt.py` | FINAL_HEADLINE·EVENT_COPY와 PROTECTED·REVEALED별 Prompt 분리, evidence 규칙과 80자 품질 제한을 검증한다. |
| `test_spoiler_guard.py` | 보호 모드의 점수·승패·결과 표현 차단과 공개 모드의 점수·승자·총득점·경기 흐름 근거 일치를 검증한다. |

## 4. 테스트 수 집계 기준

pytest의 일반 테스트와 subtest는 서로 다른 집계 단위로 구분한다.

| 구분 | 집계 방법 | 해석 |
|---|---|---|
| 테스트 파일 | `ai-service/tests/test_*.py` 파일을 집계한다. | 테스트 모듈 수를 나타낸다. |
| 일반 테스트 수집 항목 | `pytest --collect-only`에서 수집된 node ID를 집계한다. | pytest가 독립적으로 실행하는 일반 테스트 수다. |
| 일반 테스트 성공 | pytest 최종 결과의 `passed`를 확인한다. | 일반 테스트 중 성공한 항목 수다. |
| subtest 성공 | pytest 실행 결과의 subtest 성공 수를 별도로 확인한다. | 하나의 일반 테스트 안에서 추가 실행된 하위 시나리오 수다. |
| JUnit XML `tests` | XML root 또는 suite의 `tests` 속성을 확인한다. | 일반 테스트와 subtest를 합산해 표시할 수 있다. |
| JUnit XML `<testcase>` 노드 | XML의 `<testcase>` 요소를 직접 집계한다. | 일반 테스트 기준 수와 대응한다. |
| 실패·오류·스킵 | JUnit XML의 `failures`, `errors`, `skipped` 속성을 확인한다. | 성공하지 않은 실행 결과를 구분한다. |

도구에 따라 일반 테스트와 subtest의 표시 방식이 다를 수 있으므로, 실행일이 있는 결과 표에서 pytest 출력과 JUnit XML 집계를 함께 기록한다. 실행 시점의 구체적인 테스트 수는 이 절에 고정하지 않는다.

## 5. 실행 방법

### 5.1 전체 테스트

저장소 루트에서 Git Bash로 실행한다.

```bash
REPO="/c/Users/강의실/Desktop/pulse-project"
cd "$REPO" || exit 1

REPO_WIN="C:/Users/강의실/Desktop/pulse-project"

MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$REPO_WIN:/workspace" \
  -v pulse-pip-cache:/root/.cache/pip \
  -w /workspace/ai-service \
  -e PIP_ROOT_USER_ACTION=ignore \
  python:3.12-slim \
  sh -lc '
    python -m pip install \
      --disable-pip-version-check \
      -r requirements.txt \
      pytest \
      >/dev/null

    python -m pytest -q
  '
```

### 5.2 테스트 수집 결과

테스트를 실행하지 않고 일반 테스트 node ID와 수를 확인한다.

```bash
python -m pytest --collect-only -q
```

### 5.3 JUnit XML 생성

```bash
python -m pytest \
  -q \
  --junitxml=/tmp/ai-service-junit.xml
```

### 5.4 특정 파일 실행

```bash
python -m pytest \
  -q \
  tests/test_openai_service.py
```

### 5.5 특정 테스트 실행

```bash
python -m pytest \
  -q \
  tests/test_openai_service.py::OpenAiServiceTestCase::test_final_headline_uses_dedicated_timeout_and_single_attempt
```

## 6. 작성·검증 원칙

- 외부 생성 결과를 그대로 신뢰하지 않고 성공·실패·빈 응답·잘못된 JSON·timeout을 각각 검증한다.
- FINAL_HEADLINE과 EVENT_COPY의 timeout·재시도 정책을 별도 테스트로 유지한다.
- PROTECTED와 REVEALED의 허용 필드와 금지 표현을 분리해 검증한다.
- FINAL_HEADLINE은 생성 문구뿐 아니라 `usedFactIds`와 `usedPlayIds` 근거 계약을 검증한다.
- 플레이 번역은 자연스러움보다 원문의 선수명·숫자·이벤트·방향·수비 위치 보존을 우선 검증한다.
- Prompt 테스트는 실제 입력에 필요한 규칙만 포함되는지와 동일 입력의 결정성을 확인한다.
- 로그 테스트는 API Key, safe context 전문, 생성 문구 전문을 기록하지 않는지 확인한다.
- 테스트는 Python 3.12 Docker 환경에서 실행해 로컬 Python 버전 차이를 제거한다.

## 7. 실행 결과

2026-07-20 Python 3.12·pytest 9.1.1 환경에서 실행했다.

| 테스트 파일 | 일반 테스트 | 일반 테스트 성공 | subtest 성공 | 실패 | 오류 | 스킵 | 실행 시간 |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 10 | 204 | 204 | 44 | 0 | 0 | 0 | 1.62초 |

JUnit XML 집계:

| XML root | suite | `tests` | `<testcase>` 노드 | 성공 | 실패 | 오류 | 스킵 |
|---|---:|---:|---:|---:|---:|---:|---:|
| `testsuites` | 1 | 248 | 204 | 248 | 0 | 0 | 0 |

## 8. 실행 당시 경고

2026-07-20 전체 테스트 실행은 통과했지만 다음 경고 1건이 발생했다.

```text
StarletteDeprecationWarning:
Using httpx with starlette.testclient is deprecated;
install httpx2 instead.
```

현재 상태:

- 테스트 실패는 아니다.
- FastAPI TestClient 의존성 경계에서 발생하는 deprecation warning이다.
- 이번 문서 동기화 작업에서는 의존성을 변경하지 않는다.
- 향후 FastAPI·Starlette 테스트 클라이언트 의존성 업데이트 시 호환성을 별도로 검증한다.
