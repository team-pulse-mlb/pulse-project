from pydantic import BaseModel, Field


class SpoilerCheckRequest(BaseModel):
    text: str = Field(
        ...,
        description="스포일러 포함 여부를 검사할 문구",
        examples=["후반부에 긴장감이 올라간 경기입니다."],
    )


class SpoilerCheckResponse(BaseModel):
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