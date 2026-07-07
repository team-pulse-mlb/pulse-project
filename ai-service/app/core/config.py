from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """
    AI-SERVICE 전체 설정값을 관리하는 클래스.

    역할:
    - .env 파일 또는 환경변수에서 설정값을 읽는다.
    - OpenAI API Key, 모델명 등을 코드에 직접 쓰지 않게 한다.
    - 설정값이 없어도 로컬 mock 테스트가 죽지 않도록 기본값을 둔다.
    """

    app_env: str = "local"

    openai_api_key: str | None = None
    openai_model: str = "gpt-4o-mini"
    openai_temperature: float = 0.2
    openai_max_output_tokens: int = 200

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

