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
    openai_max_output_tokens: int = 200

    # Spring Boot -> ai-service timeout은 8초이므로,
    # ai-service -> OpenAI 호출은 그보다 짧은 6초 안에 끝나도록 제한한다.
    openai_timeout_seconds: float = 6.0

    # FINAL_HEADLINE/EVENT_COPY는 사용자 응답 경로와 저장 경로에서
    # 호출될 수 있으므로 SDK 내부 재시도 대신 서비스 레이어에서
    # 짧은 1회 재시도만 수행한다.
    # 기본값 기준 총 예상 시간은 3초 * 2회 + 짧은 backoff로
    # Spring Boot 호출 제한 8초 안에 들어오도록 잡는다.
    openai_ai_copy_timeout_seconds: float = 3.0
    openai_ai_copy_max_attempts: int = 2
    openai_ai_copy_retry_base_delay_seconds: float = 0.2
    openai_ai_copy_retry_max_delay_seconds: float = 0.5
    openai_ai_copy_retry_jitter_seconds: float = 0.05

    # PLAY_TRANSLATION은 배치에서 연속 호출될 수 있으므로
    # SDK 내부 재시도 대신 서비스 레이어에서 짧은 1회 재시도만 수행한다.
    # 기본값 기준 총 예상 시간은 3초 * 2회 + 짧은 backoff로
    # Spring Boot 호출 제한 8초 안에 들어오도록 잡는다.
    openai_play_translation_timeout_seconds: float = 3.0
    openai_play_translation_max_attempts: int = 2
    openai_play_translation_retry_base_delay_seconds: float = 0.2
    openai_play_translation_retry_max_delay_seconds: float = 0.5
    openai_play_translation_retry_jitter_seconds: float = 0.05

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