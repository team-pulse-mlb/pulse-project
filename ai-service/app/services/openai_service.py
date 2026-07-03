from app.schemas.ai_schema import SpoilerFreeSummaryRequest


def generate_spoiler_free_summary(request: SpoilerFreeSummaryRequest) -> dict:
    """
    경기 카드용 스포일러 없는 제목/이유/알림 문구를 생성한다.

    현재 단계:
    - 실제 OpenAI API는 아직 호출하지 않는다.
    - Spring Boot 또는 Swagger에서 받은 safe_context를 바탕으로
      mock 문구를 생성한다.
    - 나중에 이 함수 내부만 OpenAI API 호출 방식으로 교체하면 된다.

    반환값:
    - ai_router.py에서 SpoilerFreeSummaryResponse로 변환해서 응답한다.
    """

    # Spring Boot가 넘긴 안전 context를 꺼낸다.
    # 정확한 점수, 승패, 팀 우세 정보는 들어오면 안 된다.
    safe_context = request.safe_context

    # 화면에 노출할 안전 태그를 정한다.
    # Spring Boot가 safe_tags를 넘겨주면 그대로 사용하고,
    # 없으면 기본 태그를 사용한다.
    tags = safe_context.safe_tags or ["추천 구간"]

    # 경기 상태와 이닝 구간을 기준으로 mock 제목을 만든다.
    # 아직은 단순 규칙 기반 문구이며, 나중에 OpenAI 생성 문구로 교체할 예정.
    if safe_context.game_status == "UPCOMING":
        safe_title = "경기 전 확인해볼 만한 매치업"
    elif safe_context.game_status == "FINAL":
        safe_title = "다시 볼 만한 흐름이 있는 경기"
    elif safe_context.inning_phase in ["LATE", "EXTRA"]:
        safe_title = "후반부 긴장감이 올라간 경기"
    else:
        safe_title = "관전 가치가 높아진 경기"

    # 긴장도 수준에 따라 추천 이유 문구를 만든다.
    # 보호 모드에서 점수, 승패, 우세 팀을 암시하지 않는 표현만 사용한다.
    if safe_context.tension_level == "HIGH":
        safe_reason = "지금 확인해볼 만한 흐름이 감지됐습니다."
    elif safe_context.tension_level == "NORMAL":
        safe_reason = "경기 흐름을 지켜볼 만한 구간입니다."
    else:
        safe_reason = "부담 없이 확인해볼 수 있는 경기입니다."

    # 알림 문구는 짧고 안전하게 만든다.
    # 홈런, 역전, 승리, 패배, 점수 같은 직접 스포일러 표현은 사용하지 않는다.
    notification_text = "관심 경기에서 볼 만한 흐름이 감지됐어요."

    # router에서 사용하기 쉽게 dict로 반환한다.
    return {
        "safe_title": safe_title,
        "safe_reason": safe_reason,
        "notification_text": notification_text,
        "tags": tags,
    }