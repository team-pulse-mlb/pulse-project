# 라이브 시뮬레이터 가이드

연출할 예정·진행·곧 종료 경기는 `application.yml`의 `pulse.simulation.games`에 고정돼 있다. 기본 20배속에서 진행 경기는 약 9분간 재생되고, 곧 종료 경기는 종료 전이 후 실제 ai-service 호출 결과를 홈 카드에 표시한다.

1. 저장소 루트에서 원본 시드를 적재한다.

```bash
bash backend/scripts/seed-dev-slate.sh
```

이 스크립트는 `pulse-api`를 로컬 프로파일로 재생성해 DB 스키마를 준비한 뒤 시드를 적재한다.

2. 같은 `pulse-api` 컨테이너를 시뮬레이션 모드로 재생성한다.

```bash
bash backend/scripts/run-simulation.sh
```

종료 후 일반 API 모드로 되돌린다.

```bash
docker compose -f infra/local/docker-compose.yml --env-file .env up -d --force-recreate --wait pulse-api
```
