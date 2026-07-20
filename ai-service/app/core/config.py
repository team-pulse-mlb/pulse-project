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

    # 목적별 timeout 설정이 없거나 잘못된 경우 사용하는 공통 fallback입니다.
    openai_timeout_seconds: float = 6.0

    # FINAL_HEADLINE은 종료 경기용 비동기 산출물입니다.
    # 즉시 화면에 표시되는 것보다 정상적으로 생성되는 것이 중요하므로
    # EVENT_COPY보다 긴 OpenAI 호출 timeout을 사용합니다.
    #
    # 긴 단일 요청을 여러 번 반복하면 AI worker가 장시간 점유될 수 있으므로
    # 즉시 호출은 20초 1회로 제한하고, 이후 복구 재시도는 Spring Boot에서
    # 별도 작업으로 처리합니다.
    openai_final_headline_timeout_seconds: float = 20.0
    openai_final_headline_max_attempts: int = 1

    # EVENT_COPY는 라이브 경기 흐름에 가까운 문구이므로
    # 기존의 짧은 timeout과 1회 재시도 정책을 유지합니다.
    openai_event_copy_timeout_seconds: float = 3.0
    openai_event_copy_max_attempts: int = 2

    # AI-copy 서비스 레이어에서 사용하는 짧은 retry backoff 설정입니다.
    openai_ai_copy_retry_base_delay_seconds: float = 0.2
    openai_ai_copy_retry_max_delay_seconds: float = 0.5
    openai_ai_copy_retry_jitter_seconds: float = 0.05

    # PLAY_TRANSLATION은 배치에서 연속 호출될 수 있으므로
    # SDK 내부 재시도 대신 서비스 레이어에서 짧은 1회 재시도만 수행합니다.
    # 기본값 기준 총 예상 시간은 3초 * 2회 + 짧은 backoff입니다.
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