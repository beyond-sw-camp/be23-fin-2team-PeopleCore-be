# syntax=docker/dockerfile:1.4
FROM amazoncorretto:17-alpine AS stage1
WORKDIR /app

# 루트 빌드 설정 (멀티모듈)
COPY gradle gradle
COPY gradlew .
COPY gradle.properties .
COPY settings.gradle .
COPY build.gradle .
RUN chmod +x gradlew

# 빌드 대상 서비스 모듈 (common 은 GitHub Packages 에서 받음)
COPY api-gateway api-gateway

# BuildKit secret 으로 GitHub PAT 주입 (이미지 레이어에 안 남음)
RUN --mount=type=secret,id=gpr_user \
    --mount=type=secret,id=gpr_token \
    GITHUB_ACTOR="$(cat /run/secrets/gpr_user)" \
    GITHUB_TOKEN="$(cat /run/secrets/gpr_token)" \
    ./gradlew :api-gateway:bootJar -x test --no-daemon

FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=stage1 /app/api-gateway/build/libs/*.jar peoplecore.jar
ENTRYPOINT ["java", "-jar", "peoplecore.jar"]
