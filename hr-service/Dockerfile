FROM amazoncorretto:17-alpine AS stage1
WORKDIR /app
COPY gradle gradle
COPY gradlew .
COPY gradle.properties .
COPY settings.gradle .
COPY build.gradle .
RUN chmod +x gradlew
COPY hr-service hr-service
RUN --mount=type=secret,id=gpr_user \
    --mount=type=secret,id=gpr_token \
    GITHUB_ACTOR="$(cat /run/secrets/gpr_user)" \
    GITHUB_TOKEN="$(cat /run/secrets/gpr_token)" \
    ./gradlew :hr-service:bootJar -x test --no-daemon

FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=stage1 /app/hr-service/build/libs/*.jar peoplecore.jar
ENTRYPOINT ["java", "-jar", "peoplecore.jar"]
