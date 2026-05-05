FROM amazoncorretto:17-alpine AS stage1
WORKDIR /app
COPY gradle gradle
COPY build.gradle .
COPY gradlew .
COPY settings.gradle .
RUN chmod +x gradlew
COPY src src
RUN ./gradlew bootJar

FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=stage1 /app/build/libs/*.jar peoplecore.jar
ENTRYPOINT ["java", "-jar", "peoplecore.jar"]

