# syntax=docker/dockerfile:1.7
# One Dockerfile for every service:  docker build --build-arg SERVICE=order-service .
# The build stage does not depend on SERVICE, so docker compose builds the reactor once
# and every service image reuses that cached stage.

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-noble AS extract
ARG SERVICE
WORKDIR /extract
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination /extract/layers

FROM eclipse-temurin:21-jre-noble
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
# Least frequently changing layers first
COPY --from=extract /extract/layers/dependencies/ ./
COPY --from=extract /extract/layers/spring-boot-loader/ ./
COPY --from=extract /extract/layers/snapshot-dependencies/ ./
COPY --from=extract /extract/layers/application/ ./
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
