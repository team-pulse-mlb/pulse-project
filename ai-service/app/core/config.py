from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """
    AI-SERVICE 전체 설정값을 관리하는 클래스.

    역할:
    - .env 파일 또는 환경변수에서 설정값을 읽는다.
    - OpenAI API Key, 모델명, timeout 같은 외부 설정을 코드와 분리한다.
    - 로컬 테스트에서 설정값이 일부 비어 있어도 앱 import 자체가 깨지지 않게 기본값을 둔다.
    """

    app_env: str = "local"

    openai_api_key: str | None = None
    openai_model: str = "gpt-5.6-luna"
    openai_temperature: float = 0.2

    # 짧은 AI 문구 생성은 복잡한 추론이 필요하지 않으므로
    # reasoning token 사용량과 지연시간을 줄이기 위해 low로 제한합니다.
    openai_reasoning_effort: str = "low"

    # reasoning 모델은 내부 reasoning token도 max_output_tokens를 사용합니다.
    # 200에서는 visible output 전에 한도가 끝날 수 있어 여유를 확보합니다.
    openai_max_output_tokens: int = 1024

    # Spring Boot -> ai-service timeout은 8초이므로,
    # ai-service -> OpenAI 호출은 그보다 짧은 6초 안에 끝나도록 제한한다.
    openai_timeout_seconds: float = 6.0

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    """
    Settings 객체를 한 번만 생성해서 재사용한다.
    """

    return Settings()


settings = get_settings()
