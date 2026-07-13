from enum import Enum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class ApiBaseModel(BaseModel):
    """
    모든 API schema가 공통으로 사용하는 기본 모델입니다.

    역할:
    - Python 내부에서는 snake_case 필드명을 사용한다.
    - 외부 JSON에서는 Spring Boot / frontend 기준 camelCase 필드명을 허용한다.
    - 아직 계약이 확정되지 않은 추가 필드는 무시해서 Spring Boot 변경에 조금 더 안전하게 대응한다.
    """

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="ignore",
    )


class AiCopyMode(str, Enum):
    """
    AI 문구 생성 모드입니다.

    PROTECTED:
    - 점수, 승패, 우세 팀, 결과 방향성 표현을 포함하면 안 된다.

    REVEALED:
    - 요청 safeContext에 포함된 최종 점수, 승자, 이벤트 근거 범위 안에서만 결과 언급이 가능하다.
    """

    PROTECTED = "PROTECTED"
    REVEALED = "REVEALED"


class FinalScore(ApiBaseModel):
    """
    공개 모드 종료 헤드라인에서만 사용할 수 있는 최종 점수 정보입니다.

    mode=PROTECTED 요청에는 포함하지 않는다.
    mode=REVEALED 응답 검수 시, AI가 언급한 점수가 이 값과 일치하는지 확인하는 기준이 된다.
    """

    home: int = Field(..., examples=[5])
    away: int = Field(..., examples=[3])


class KeyMoment(ApiBaseModel):
    """
    종료 경기 헤드라인에 사용할 수 있는 스포일러 안전 핵심 순간입니다.

    label은 화면에 노출 가능한 보호 표현만 받는다.
    """

    inning: int | None = Field(
        default=None,
        description="보호 모드에서도 노출 가능한 이닝 숫자. 필요 없으면 null",
        examples=[7],
    )
    label: str = Field(
        ...,
        description="스포일러 없는 핵심 순간 라벨",
        examples=["만루 승부"],
    )


class SafeContext(ApiBaseModel):
    """
    Spring Boot가 ai-service로 넘겨주는 스포일러 안전 context입니다.

    FINAL_HEADLINE과 EVENT_COPY가 같은 safeContext 이름을 공유하되,
    endpoint별로 필요한 필드만 채워서 사용한다.

    주의:
    - raw play 원문은 받지 않는다.
    - 타석 결과 원문은 받지 않는다.
    - mode=PROTECTED에서는 점수, 승패, 우세 팀을 받지 않는다.
    - mode=REVEALED에서만 finalScore, winner, 이벤트 근거 필드를 받을 수 있다.
    """

    # FINAL_HEADLINE 공통 필드
    game_status: str | None = Field(
        default=None,
        description="Spring Boot 경기 상태값. 종료 헤드라인 생성 대상은 STATUS_FINAL",
        examples=["STATUS_FINAL"],
    )
    inning_phase: str | None = Field(
        default=None,
        description="스포일러 없는 경기 구간 표현",
        examples=["경기 종료"],
    )
    tension_level: str | None = Field(
        default=None,
        description="Spring Boot가 계산한 긴장도 라벨",
        examples=["HIGH"],
    )
    score_band: str | None = Field(
        default=None,
        description="Spring Boot가 계산한 추천/흐름 라벨",
        examples=["RECOMMEND"],
    )
    safe_tags: list[str] = Field(
        default_factory=list,
        description="사용자에게 노출 가능한 안전 태그",
        examples=[["후반 긴장 구간"]],
    )
    reason_codes: list[str] = Field(
        default_factory=list,
        description="스포일러 guard와 프롬프트 제어에 사용할 내부 reason code",
        examples=[["late_or_extra"]],
    )
    key_moments: list[KeyMoment] = Field(
        default_factory=list,
        description="헤드라인 생성에 사용할 수 있는 스포일러 안전 핵심 순간 목록",
    )

    # FINAL_HEADLINE mode=REVEALED 전용 필드
    final_score: FinalScore | None = Field(
        default=None,
        description="공개 모드에서만 사용할 수 있는 최종 점수",
    )
    winner: str | None = Field(
        default=None,
        description="공개 모드에서만 사용할 수 있는 승자 정보. 예: home, away, draw",
        examples=["home"],
    )

    # EVENT_COPY 공통 필드
    event_type: str | None = Field(
        default=None,
        description="이벤트 타입",
        examples=["pressure_bases_loaded"],
    )
    label: str | None = Field(
        default=None,
        description="이벤트 타임라인에 표시 가능한 보호 라벨",
        examples=["만루 승부"],
    )
    inning: int | None = Field(
        default=None,
        description="이벤트 발생 이닝",
        examples=[7],
    )

    # EVENT_COPY mode=REVEALED 전용 필드
    inning_type: str | None = Field(
        default=None,
        description="공개 모드에서만 사용할 수 있는 초/말 정보",
        examples=["Top"],
    )
    batter: str | None = Field(
        default=None,
        description="공개 모드에서만 사용할 수 있는 타자명",
        examples=["Kim"],
    )
    pitcher: str | None = Field(
        default=None,
        description="공개 모드에서만 사용할 수 있는 투수명",
        examples=["Steele"],
    )
    evidence: dict[str, Any] | None = Field(
        default=None,
        description="공개 모드 이벤트 문구 생성을 위한 제한된 근거 데이터",
        examples=[{"outs": 2, "balls": 3, "strikes": 2}],
    )


class FinalHeadlineRequest(ApiBaseModel):
    """
    POST /ai/final-headline 요청 형식입니다.
    """

    game_id: int = Field(..., examples=[5058990])
    mode: AiCopyMode = Field(..., examples=["PROTECTED"])
    context_hash: str = Field(
        ...,
        description="Spring Boot가 최신 context 여부를 검증하기 위해 전달하는 필수 해시값",
        examples=["game-5058990-final-v3"],
    )
    safe_context: SafeContext = Field(...)


class EventCopyRequest(ApiBaseModel):
    """
    POST /ai/event-copy 요청 형식입니다.
    """

    game_id: int = Field(..., examples=[5059041])
    event_id: int = Field(..., examples=[91])
    mode: AiCopyMode = Field(..., examples=["PROTECTED"])
    context_hash: str = Field(
        ...,
        description="Spring Boot가 최신 event context 여부를 검증하기 위해 전달하는 필수 해시값",
        examples=["event-91-v1"],
    )
    safe_context: SafeContext = Field(...)


class AiCopyResponse(ApiBaseModel):
    """
    FINAL_HEADLINE / EVENT_COPY 공통 응답 형식입니다.

    실패 또는 검수 불통과 시 safe_title은 내려주지 않는다.
    fallback_used는 현재 계약에서 항상 false를 반환한다.
    """

    spoiler_safe: bool = Field(
        ...,
        description="AI 생성 문구가 spoiler guard를 통과했는지 여부",
    )
    context_hash: str = Field(
        ...,
        description="요청에서 받은 contextHash를 그대로 반환한 값",
    )
    safe_title: str | None = Field(
        default=None,
        description="검수 통과한 AI 문구. 실패 시 null 또는 생략 가능",
    )
    violations: list[str] = Field(
        default_factory=list,
        description="스포일러 검사 또는 생성 실패 코드 목록",
    )
    fallback_used: bool = Field(
        default=False,
        description="ai-service는 대체 문구를 만들지 않으므로 현재 계약에서는 항상 false",
    )


class FinalHeadlineResponse(AiCopyResponse):
    """
    POST /ai/final-headline 응답 형식입니다.
    """


class EventCopyResponse(AiCopyResponse):
    """
    POST /ai/event-copy 응답 형식입니다.
    """