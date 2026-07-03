import re


# 보호 모드에서 사용자에게 직접 노출되면 안 되는 단어 목록
# 예: 홈런, 역전, 승리/패배 등은 경기 결과나 흐름을 강하게 암시할 수 있음
FORBIDDEN_WORDS = [
    "홈런",
    "역전",
    "끝내기",
    "승리",
    "패배",
    "리드",
    "우세",
    "열세",
    "결승타",
    "동점타",
    "쐐기",
]


# 점수 표현을 감지하기 위한 정규식 목록
# 예: 3대2, 3:2, 3-2 같은 표현은 스포일러가 될 수 있음
SCORE_PATTERNS = [
    r"\d+\s*대\s*\d+",
    r"\d+\s*:\s*\d+",
    r"\d+\s*-\s*\d+",
]


# 스포일러가 감지됐을 때 대신 보여줄 기본 안전 문구
DEFAULT_FALLBACK_TEXT = "지금 확인해볼 만한 흐름이 감지됐습니다."

def check_spoiler_text(text: str) -> dict:
    """
    입력된 문구에 스포일러성 표현이 포함되어 있는지 검사한다.

    검사 대상:
    - 금지어: 홈런, 역전, 승리, 패배 등
    - 점수 패턴: 3대2, 3:2, 3-2 등

    반환값:
    - spoiler_safe: 스포일러가 없으면 True, 있으면 False
    - violations: 감지된 위반 항목 목록
    - fallback_text: 스포일러가 감지됐을 때 사용할 안전 문구
    """

    # 감지된 스포일러 위반 항목을 저장하는 리스트
    violations: list[str] = []

    # 금지어가 문장 안에 포함되어 있는지 검사
    for word in FORBIDDEN_WORDS:
        if word in text:
            violations.append(f"FORBIDDEN_WORD:{word}")

    # 점수 표현이 문장 안에 포함되어 있는지 검사
    for pattern in SCORE_PATTERNS:
        if re.search(pattern, text):
            violations.append("SCORE_PATTERN")
            break

    # 위반 항목이 하나도 없으면 안전한 문구로 판단
    spoiler_safe = len(violations) == 0

    # 검사 결과를 dict 형태로 반환
    # 이 key 이름은 SpoilerCheckResponse schema와 맞춰야 함
    return {
        "spoiler_safe": spoiler_safe,
        "violations": violations,
        "fallback_text": None if spoiler_safe else DEFAULT_FALLBACK_TEXT,
    }