from enum import Enum
from typing import Any, Literal

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


class Team(ApiBaseModel):
    """
    FINAL_HEADLINE v2에서 사용하는 팀 기본 정보입니다.
    """

    name: str | None = Field(default=None, examples=["Los Angeles Dodgers"])
    abbr: str | None = Field(default=None, examples=["LAD"])


class Teams(ApiBaseModel):
    """
    홈팀/원정팀 정보를 담는 FINAL_HEADLINE v2 팀 컨텍스트입니다.
    """

    home: Team | None = Field(default=None)
    away: Team | None = Field(default=None)


class PlayerInfo(ApiBaseModel):
    """
    FINAL_HEADLINE v2에서 플레이/이벤트 근거에 연결된 선수 정보입니다.
    """

    id: int | None = Field(default=None, examples=[660271])
    name: str | None = Field(default=None, examples=["Shohei Ohtani"])


class ScoreAfter(ApiBaseModel):
    """
    특정 공개 순간 이후의 홈/원정 점수입니다.
    """

    home: int | None = Field(default=None, examples=[5])
    away: int | None = Field(default=None, examples=[3])


class SummaryFacts(ApiBaseModel):
    """
    Spring Boot가 계산해서 전달하는 FINAL_HEADLINE v2 경기 요약 사실입니다.
    """

    winner_side: str | None = Field(default=None, examples=["home"])
    winner_name: str | None = Field(default=None, examples=["Los Angeles Dodgers"])
    loser_name: str | None = Field(default=None, examples=["San Francisco Giants"])
    winner_score: int | None = Field(default=None, examples=[5])
    loser_score: int | None = Field(default=None, examples=[3])

    first_scoring_side: str | None = Field(default=None, examples=["home"])
    first_scoring_inning: int | None = Field(default=None, examples=[2])

    tying_inning: int | None = Field(default=None, examples=[7])
    decisive_inning: int | None = Field(default=None, examples=[8])
    decisive_runs: int | None = Field(default=None, examples=[2])

    lead_change_count: int | None = Field(default=None, examples=[2])
    comeback_win: bool | None = Field(default=None, examples=[True])
    walk_off: bool | None = Field(default=None, examples=[False])
    shutout: bool | None = Field(default=None, examples=[False])
    extra_innings: bool | None = Field(default=None, examples=[True])
    final_inning: int | None = Field(default=None, examples=[10])

    score_gap: int | None = Field(default=None, examples=[2])
    total_runs: int | None = Field(default=None, examples=[8])


class RevealedEvent(ApiBaseModel):
    """
    FINAL_HEADLINE v2 공개 모드 헤드라인에 사용할 수 있는 공개 이벤트 근거입니다.
    """

    event_id: int | None = Field(default=None, examples=[91])
    event_type: str | None = Field(default=None, examples=["home_run"])
    inning: int | None = Field(default=None, examples=[8])
    inning_type: str | None = Field(default=None, examples=["Bottom"])
    batter: PlayerInfo | None = Field(default=None)
    pitcher: PlayerInfo | None = Field(default=None)
    evidence: dict[str, Any] = Field(default_factory=dict)


class RevealedMoment(ApiBaseModel):
    """
    기존 backend 공개 순간 요약 구조를 ai-service에서 수신하기 위한 모델입니다.
    """

    inning: int | None = Field(default=None, examples=[8])
    inning_half: str | None = Field(default=None, examples=["Bottom"])
    batting_team: str | None = Field(default=None, examples=["LAD"])
    event_types: list[str] = Field(default_factory=list)
    batter: str | None = Field(default=None, examples=["Shohei Ohtani"])
    runs_scored: int | None = Field(default=None, examples=[2])
    score_after: ScoreAfter | None = Field(default=None)
    scoring_plays: int | None = Field(default=None, examples=[1])


class VerifiedPlay(ApiBaseModel):
    """
    FINAL_HEADLINE v2 공개 모드 헤드라인에 사용할 수 있는 검증된 play 근거입니다.
    """

    play_id: int | None = Field(default=None, examples=[312])
    play_order: int | None = Field(default=None, examples=[4250312])

    inning: int | None = Field(default=None, examples=[8])
    inning_type: str | None = Field(default=None, examples=["bottom"])

    source_text: str | None = Field(default=None, examples=["Ohtani homered to right center."])
    translated_text: str | None = Field(default=None, examples=["Ohtani, 우중간 홈런"])

    home_score_after: int | None = Field(default=None, examples=[5])
    away_score_after: int | None = Field(default=None, examples=[3])

    scoring_play: bool | None = Field(default=None, examples=[True])
    score_value: int | None = Field(default=None, examples=[2])

    outs: int | None = Field(default=None, examples=[1])
    balls: int | None = Field(default=None, examples=[2])
    strikes: int | None = Field(default=None, examples=[1])

    batter: PlayerInfo | None = Field(default=None)
    pitcher: PlayerInfo | None = Field(default=None)

    runner_on_first: bool | None = Field(default=None, examples=[False])
    runner_on_second: bool | None = Field(default=None, examples=[True])
    runner_on_third: bool | None = Field(default=None, examples=[False])

    fact_tags: list[str] = Field(default_factory=list)


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

    # FINAL_HEADLINE v2 mode=REVEALED 전용 필드
    status: str | None = Field(default=None, examples=["STATUS_FINAL"])
    period_label: str | None = Field(default=None, examples=["경기 종료"])
    teams: Teams | None = Field(default=None)
    innings_played: int | None = Field(default=None, examples=[9])
    extra_innings: bool | None = Field(default=None, examples=[False])
    postseason: bool | None = Field(default=None, examples=[False])
    venue: str | None = Field(default=None, examples=["Dodger Stadium"])
    start_time: str | None = Field(default=None, examples=["2026-07-03T00:05:00Z"])
    home_inning_scores: list[int] = Field(default_factory=list)
    away_inning_scores: list[int] = Field(default_factory=list)
    summary_facts: SummaryFacts | None = Field(default=None)
    revealed_events: list[RevealedEvent] = Field(default_factory=list)
    revealed_moments: list[RevealedMoment] = Field(default_factory=list)
    verified_plays: list[VerifiedPlay] = Field(default_factory=list)

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
    contributing_labels: list[str] | None = Field(
        default=None,
        description="EVENT_COPY 보호 모드에서 같은 하이라이트 순간에 함께 감지된 보호 라벨 목록",
        examples=[["만루 승부", "승부처 카운트"]],
    )
    situation: dict[str, Any] | None = Field(
        default=None,
        description="EVENT_COPY 보호 모드에서 사용할 수 있는 카운트·아웃·주자·투구수 기반 상황 근거",
        examples=[
            {
                "outs": 2,
                "balls": 3,
                "strikes": 2,
                "runnerOnFirst": True,
                "runnerOnSecond": True,
                "runnerOnThird": True,
            }
        ],
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


class PlayTranslationRequest(ApiBaseModel):
    """
    POST /ai/play-translation 요청 형식입니다.

    공개 모드 최근 플레이에 표시할 단일 Play Result 원문만 번역합니다.

    주의:
    - PLAY_TRANSLATION은 REVEALED 모드만 허용합니다.
    - sourceText 외에 점수, 팀, 추천 점수, 다른 play 문맥은 받지 않습니다.
    - targetLanguage는 현재 한국어 ko만 허용합니다.
    """

    game_id: int = Field(
        ...,
        gt=0,
        description="번역 대상 플레이가 속한 경기 ID",
        examples=[5059041],
    )
    play_id: int = Field(
        ...,
        gt=0,
        description="번역 대상 Play Result의 내부 play ID",
        examples=[312],
    )
    mode: Literal[AiCopyMode.REVEALED] = Field(
        ...,
        description="PLAY_TRANSLATION은 공개 모드 REVEALED만 허용",
        examples=["REVEALED"],
    )
    context_hash: str = Field(
        ...,
        min_length=1,
        description="Spring Boot가 최신 원문 여부를 검증하기 위해 전달하는 필수 해시값",
        examples=["play-312-v1"],
    )
    source_text: str = Field(
        ...,
        min_length=1,
        description="번역할 단일 MLB Play Result 원문",
        examples=["Marsh struck out looking."],
    )
    target_language: Literal["ko"] = Field(
        ...,
        description="현재 지원하는 번역 대상 언어. 한국어 ko만 허용",
        examples=["ko"],
    )


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

    used_fact_ids:
    - 생성 문구가 실제로 사용했다고 선언한 summaryFacts 필드 ID 목록

    used_play_ids:
    - 생성 문구가 실제로 사용했다고 선언한 verifiedPlay의 playId 목록
    """

    used_fact_ids: list[str] = Field(
        default_factory=list,
        description=(
            "FINAL_HEADLINE 생성에 실제 사용한 summaryFacts 근거 ID 목록"
        ),
        examples=[
            [
                "summaryFacts.comebackWin",
                "summaryFacts.walkOff",
                "summaryFacts.decisiveInning",
            ]
        ],
    )
    used_play_ids: list[int] = Field(
        default_factory=list,
        description=(
            "FINAL_HEADLINE 생성에 실제 사용한 verifiedPlay playId 목록"
        ),
        examples=[[312]],
    )


class EventCopyResponse(AiCopyResponse):
    """
    POST /ai/event-copy 응답 형식입니다.
    """


class PlayTranslationResponse(ApiBaseModel):
    """
    POST /ai/play-translation 응답 형식입니다.

    생성 또는 검수 실패 시 translated_text는 null이거나 응답에서 생략할 수 있습니다.
    ai-service는 대체 번역을 만들지 않으므로 fallback_used는 항상 false입니다.
    """

    translated_text: str | None = Field(
        default=None,
        description="원문의 선수명, 숫자, 야구 결과를 보존한 한국어 번역",
        examples=["Marsh, 루킹 삼진"],
    )
    violations: list[str] = Field(
        default_factory=list,
        description="번역 생성 실패 또는 번역 검수 위반 코드 목록",
    )
    fallback_used: bool = Field(
        default=False,
        description="ai-service는 대체 번역을 만들지 않으므로 항상 false",
    )
    context_hash: str = Field(
        ...,
        description="요청에서 받은 contextHash를 그대로 반환한 값",
        examples=["play-312-v1"],
    )