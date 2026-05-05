# syntax=docker/dockerfile:1.4
FROM amazoncorretto:17-alpine AS stage1
WORKDIR /app
COPY gradle gradle
COPY gradlew .
COPY gradle.properties .
COPY settings.gradle .
COPY build.gradle .
RUN chmod +x gradlew
COPY collaboration-service collaboration-service
RUN --mount=type=secret,id=gpr_user \
    --mount=type=secret,id=gpr_token \
    GITHUB_ACTOR="$(cat /run/secrets/gpr_user)" \
    GITHUB_TOKEN="$(cat /run/secrets/gpr_token)" \
    ./gradlew :hr-service:bootJar -x test --no-daemon

FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=stage1 /app/collaboration-service/build/libs/*.jar peoplecore.jar
ENTRYPOINT ["java", "-jar", "peoplecore.jar"]
