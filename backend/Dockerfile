# PULSE backend 운영 이미지.
# 부팅 jar를 빌드 컨텍스트에 pulse.jar로 두고 빌드하는 런타임 전용 이미지다.
# Gradle 빌드는 이미지 밖(로컬 또는 CI)에서 수행한다.
#
# 로컬 빌드 예시:
#   .\gradlew.bat bootJar
#   docker build -t pulse-app --build-arg JAR_FILE=build/libs/pulse-0.0.1-SNAPSHOT.jar .
FROM eclipse-temurin:21-jre-noble

# actuator 헬스체크 호출용 curl
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --shell /usr/sbin/nologin pulse

WORKDIR /app

ARG JAR_FILE=pulse.jar
COPY ${JAR_FILE} /app/pulse.jar

USER pulse

# 힙 상한 등 JVM 인자는 배포 환경(Compose의 JAVA_OPTS)에서 지정한다.
ENV JAVA_OPTS=""

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/pulse.jar"]
