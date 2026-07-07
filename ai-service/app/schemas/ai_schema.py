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
        extra="ignore",
    )


class SpoilerCheckRequest(ApiBaseModel):
    """
    /ai/spoiler-check 요청 형식
    """

    text: str = Field(
        ...,
        description="스포일러 포함 여부를 검사할 문구",
        examples=["후반부에 긴장감이 올라간 경기입니다."],
    )


class SpoilerCheckResponse(ApiBaseModel):
    """
    /ai/spoiler-check 응답 형식
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
        description="스포일러가 감지됐을 때 Spring에서 fallback 처리할 수 있는 안전 상태 문구",
    )


class SafeContext(ApiBaseModel):
    """
    Spring Boot가 Python AI-SERVICE로 넘겨주는 안전한 경기 context

    Spring 응답 매핑 기준:
    - status -> game_status
    - periodLabel -> inning_phase
    - reasonTags -> safe_tags
    - spoilerSafeSignals -> reason_codes

    recentPlays는 실제 플레이 텍스트와 선수명을 포함할 수 있으므로
    ai-service 요청에 넣지 않는다.
    """

    game_status: str = Field(
        ...,
        description="Spring Boot 경기 상태값",
        examples=["STATUS_SCHEDULED", "STATUS_LIVE", "STATUS_FINAL"],
    )
    inning_phase: str = Field(..., examples=["경기 종료"])
    tension_level: str = Field(default="NORMAL", examples=["NORMAL"])
    score_band: str = Field(default="RECOMMEND", examples=["RECOMMEND"])
    safe_tags: list[str] = Field(default_factory=list)
    reason_codes: list[str] = Field(default_factory=list)


class SpoilerFreeSummaryRequest(ApiBaseModel):
    """
    /ai/spoiler-free-summary 요청 형식
    """

    game_id: int = Field(..., examples=[12345])
    mode: str = Field(default="PROTECTED", examples=["PROTECTED"])
    surface: str = Field(default="HOME_CARD", examples=["HOME_CARD"])
    language: str = Field(default="ko", examples=["ko"])
    max_length: int = Field(default=80, examples=[80])
    context_hash: str | None = Field(
        default=None,
        description="Spring Boot가 최신 context 여부를 검증하기 위해 전달하는 해시값",
        examples=["game-5059082-status-final-v3"],
    )
    safe_context: SafeContext = Field(...)


class SpoilerFreeSummaryResponse(ApiBaseModel):
    """
    /ai/spoiler-free-summary 응답 형식
    """

    spoiler_safe: bool
    context_hash: str | None = Field(
        default=None,
        description="요청에서 받은 contextHash를 그대로 반환한 값",
    )
    safe_title: str | None = None
    safe_reason: str | None = None
    notification_text: str | None = None
    tags: list[str] = Field(default_factory=list)
    violations: list[str] = Field(default_factory=list)
    fallback_used: bool = False


class NotificationTextRequest(SpoilerFreeSummaryRequest):
    """
    /ai/notification-text 요청 형식

    SpoilerFreeSummaryRequest와 같은 safe_context를 사용하되,
    알림이 사용될 채널 정보만 추가로 받는다.
    """

    channel: str = Field(
        default="WEB",
        description="알림 채널 예: WEB, APP, SSE",
        examples=["WEB"],
    )


class NotificationTextResponse(ApiBaseModel):
    """
    /ai/notification-text 응답 형식
    """

    spoiler_safe: bool = Field(
        ...,
        description="최종 알림 문구가 스포일러 안전 정책을 통과했는지 여부",
    )
    context_hash: str | None = Field(
        default=None,
        description="요청에서 받은 contextHash를 그대로 반환한 값",
    )
    notification_text: str | None = Field(
        default=None,
        description="사용자에게 보낼 스포일러 없는 알림 문구",
    )
    tags: list[str] = Field(
        default_factory=list,
        description="알림과 함께 사용할 수 있는 안전 태그",
    )
    violations: list[str] = Field(
        default_factory=list,
        description="스포일러 검사에서 감지된 위반 항목",
    )
    fallback_used: bool = Field(
        default=False,
        description="생성 문구 대신 fallback 문구를 사용했는지 여부",
    )


class ReplaySummaryRequest(SpoilerFreeSummaryRequest):
    """
    /ai/replay-summary 요청 형식

    SpoilerFreeSummaryRequest와 같은 safe_context를 사용하되,
    다시보기 구간을 식별하고 설명할 수 있는 안전한 정보만 추가로 받는다.
    """

    replay_segment_id: str | None = Field(
        default=None,
        description="다시보기 구간 ID. 없으면 null",
        examples=["segment-5059082-001"],
    )
    segment_label: str = Field(
        default="스포일러 없이 다시 보기 좋은 구간",
        description="사용자에게 노출 가능한 다시보기 구간 라벨",
        examples=["스포일러 없이 다시 보기 좋은 구간"],
    )
    segment_reason_tags: list[str] = Field(
        default_factory=list,
        description="다시보기 구간을 설명하는 안전 태그",
        examples=[["후반 긴장 구간", "흐름 변화 가능성"]],
    )


class ReplaySummaryResponse(ApiBaseModel):
    """
    /ai/replay-summary 응답 형식
    """

    spoiler_safe: bool = Field(
        ...,
        description="최종 다시보기 문구가 스포일러 안전 정책을 통과했는지 여부",
    )
    context_hash: str | None = Field(
        default=None,
        description="요청에서 받은 contextHash를 그대로 반환한 값",
    )
    replay_title: str | None = Field(
        default=None,
        description="스포일러 없는 다시보기 제목",
    )
    replay_summary: str | None = Field(
        default=None,
        description="스포일러 없는 다시보기 설명 문구",
    )
    tags: list[str] = Field(
        default_factory=list,
        description="다시보기 문구와 함께 사용할 수 있는 안전 태그",
    )
    violations: list[str] = Field(
        default_factory=list,
        description="스포일러 검사에서 감지된 위반 항목",
    )
    fallback_used: bool = Field(
        default=False,
        description="생성 문구 대신 fallback 문구를 사용했는지 여부",
    )