from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class ApiBaseModel(BaseModel):
    """
    모든 API schema가 공통으로 사용하는 기본 모델입니다.

    역할:
    - Python 내부에서는 snake_case 필드명을 사용
    - 외부 JSON에서는 camelCase 필드명도 허용
    - 로컬 테스트에서는 snake_case로도 입력 가능
    """

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
    )


class SpoilerCheckRequest(ApiBaseModel):
    """
    /ai/spoiler-check 요청 형식

    역할:
    - 사용자가 노출하려는 문구가 스포일러인지 검사할 때 사용
    """

    text: str = Field(
        ...,
        description="스포일러 포함 여부를 검사할 문구",
        examples=["후반부에 긴장감이 올라간 경기입니다."],
    )


class SpoilerCheckResponse(ApiBaseModel):
    """
    /ai/spoiler-check 응답 형식

    역할:
    - 문구가 안전한지 여부
    - 감지된 위반 항목
    - 위험할 때 사용할 fallback 문구를 반환
    """

    spoiler_safe: bool = Field(
        ...,
        description="스포일러가 없으면 true, 있으면 false",
    )
    violations: list[str] = Field(
        default_factory=list,
        description="감지된 스포일러 위반 항목",
    )
    fallback_text: str | None = Field(
        default=None,
        description="스포일러가 감지됐을 때 대체할 안전 문구",
    )


class SafeContext(ApiBaseModel):
    """
    Spring Boot가 Python AI-SERVICE로 넘겨주는 안전한 경기 context

    주의:
    - 정확한 점수, 승패, 팀 우세 정보는 포함하지 않는다.
    - AI는 이 안전한 context만 보고 문구를 생성한다.
    """

    game_status: str = Field(
        ...,
        description="경기 상태 예: LIVE, UPCOMING, FINAL",
        examples=["LIVE"],
    )
    inning_phase: str = Field(
        ...,
        description="이닝 구간 예: EARLY, MIDDLE, LATE, EXTRA",
        examples=["LATE"],
    )
    tension_level: str = Field(
        ...,
        description="긴장도 수준 예: LOW, NORMAL, HIGH",
        examples=["HIGH"],
    )
    score_band: str = Field(
        ...,
        description="추천 강도 예: LOW, NORMAL, RECOMMEND, STRONGLY_RECOMMEND",
        examples=["RECOMMEND"],
    )
    safe_tags: list[str] = Field(
        default_factory=list,
        description="사용자에게 노출 가능한 안전 태그",
        examples=[["후반 긴장 구간", "흐름 변화 가능성"]],
    )
    reason_codes: list[str] = Field(
        default_factory=list,
        description="추천 이유를 코드로 표현한 값",
        examples=[["LATE_INNING", "CLOSE_GAME"]],
    )


class SpoilerFreeSummaryRequest(ApiBaseModel):
    """
    /ai/spoiler-free-summary 요청 형식

    역할:
    - Spring Boot가 계산한 안전 context를 받아
      경기 카드용 스포일러 없는 제목/이유 문구를 생성할 때 사용
    """

    game_id: int = Field(
        ...,
        description="경기 ID",
        examples=[12345],
    )
    mode: str = Field(
        default="PROTECTED",
        description="화면 모드. 기본은 스포일러 보호 모드",
        examples=["PROTECTED"],
    )
    surface: str = Field(
        default="HOME_CARD",
        description="문구가 사용될 화면 위치",
        examples=["HOME_CARD"],
    )
    language: str = Field(
        default="ko",
        description="응답 언어",
        examples=["ko"],
    )
    max_length: int = Field(
        default=80,
        description="생성 문구 최대 길이",
        examples=[80],
    )
    safe_context: SafeContext = Field(
        ...,
        description="스포일러 없는 안전 경기 context",
    )


class SpoilerFreeSummaryResponse(ApiBaseModel):
    """
    /ai/spoiler-free-summary 응답 형식

    역할:
    - 사용자에게 보여줄 안전한 제목, 이유, 알림 문구를 반환
    - 최종 반환 전 spoiler_guard.py 검수를 통과해야 한다.
    """

    spoiler_safe: bool = Field(
        ...,
        description="최종 문구가 스포일러 안전 정책을 통과했는지 여부",
    )
    safe_title: str = Field(
        ...,
        description="스포일러 없는 카드 제목",
        examples=["후반부 긴장감이 올라간 경기"],
    )
    safe_reason: str = Field(
        ...,
        description="스포일러 없는 추천 이유",
        examples=["지금 확인해볼 만한 흐름이 감지됐습니다."],
    )
    notification_text: str | None = Field(
        default=None,
        description="알림에 사용할 수 있는 짧은 문구",
    )
    tags: list[str] = Field(
        default_factory=list,
        description="화면에 노출 가능한 안전 태그",
    )
    violations: list[str] = Field(
        default_factory=list,
        description="스포일러 검사에서 감지된 위반 항목",
    )
    fallback_used: bool = Field(
        default=False,
        description="AI 문구 대신 fallback 문구를 사용했는지 여부",
    )