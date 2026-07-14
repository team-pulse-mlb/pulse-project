#!/usr/bin/env python3
"""Secrets Manager 값을 운영 .env에 원자적으로 동기화한다."""

import json
import os
import pathlib
import subprocess
import tempfile


ENV_PATH = pathlib.Path(os.environ.get("PULSE_ENV_FILE", "/home/ubuntu/pulse-runtime/.env"))
AWS_REGION = os.environ.get("AWS_REGION", "ap-northeast-2")
RUNTIME_SECRET_ID = os.environ.get("PULSE_RUNTIME_SECRET_ID", "")
RDS_SECRET_ID = os.environ["PULSE_RDS_SECRET_ID"]


def get_secret(secret_id: str) -> dict[str, object]:
    secret_text = subprocess.check_output(
        [
            "aws",
            "secretsmanager",
            "get-secret-value",
            "--region",
            AWS_REGION,
            "--secret-id",
            secret_id,
            "--query",
            "SecretString",
            "--output",
            "text",
        ],
        text=True,
    )
    value = json.loads(secret_text)
    if not isinstance(value, dict):
        raise ValueError(f"시크릿은 JSON 객체여야 한다: {secret_id}")
    return value


def desired_values() -> dict[str, str]:
    values: dict[str, str] = {}
    if RUNTIME_SECRET_ID:
        values.update(
            {
                str(key): str(value)
                for key, value in get_secret(RUNTIME_SECRET_ID).items()
                if value is not None
            }
        )

    rds = get_secret(RDS_SECRET_ID)
    mapping = {
        "POSTGRES_HOST": "host",
        "POSTGRES_PORT": "port",
        "POSTGRES_DB": "dbname",
        "POSTGRES_USER": "username",
        "POSTGRES_PASSWORD": "password",
    }
    for environment_key, secret_key in mapping.items():
        if secret_key in rds and rds[secret_key] is not None:
            values[environment_key] = str(rds[secret_key])
    return values


def merge_env(current: str, updates: dict[str, str]) -> str:
    def literal(value: str) -> str:
        # Compose .env의 단일 인용 값은 $를 변수로 치환하지 않는다.
        escaped = value.replace("\\", "\\\\").replace("'", "\\'")
        return f"'{escaped}'"

    result: list[str] = []
    seen: set[str] = set()
    for line in current.splitlines():
        key = line.split("=", 1)[0] if "=" in line else None
        if key in updates:
            result.append(f"{key}={literal(updates[key])}")
            seen.add(key)
        else:
            result.append(line)
    for key, value in updates.items():
        if key not in seen:
            result.append(f"{key}={literal(value)}")
    return "\n".join(result) + "\n"


def main() -> None:
    current = ENV_PATH.read_text(encoding="utf-8") if ENV_PATH.exists() else ""
    desired = merge_env(current, desired_values())
    if desired == current:
        print("unchanged")
        return

    ENV_PATH.parent.mkdir(parents=True, exist_ok=True)
    file_descriptor, temporary_name = tempfile.mkstemp(
        prefix=".env.", dir=ENV_PATH.parent, text=True
    )
    try:
        with os.fdopen(file_descriptor, "w", encoding="utf-8", newline="\n") as temporary:
            temporary.write(desired)
        os.chmod(temporary_name, 0o600)
        if ENV_PATH.exists():
            stat = ENV_PATH.stat()
            os.chown(temporary_name, stat.st_uid, stat.st_gid)
        os.replace(temporary_name, ENV_PATH)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)
    print("changed")


if __name__ == "__main__":
    main()
