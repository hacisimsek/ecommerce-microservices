# build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY . .

# Install parent POM first
RUN mvn install -N -B --no-transfer-progress

# Install common-library into .m2 BEFORE building order-service
RUN mvn -pl common-library clean install -DskipTests -B --no-transfer-progress

#  Now build order-service safely
RUN mvn -pl order-service clean package -DskipTests -B --no-transfer-progress

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN apt-get update && \
    apt-get upgrade -y --no-install-recommends && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 1001 springgroup && \
    useradd --system --uid 1001 --gid springgroup \
            --no-create-home --shell /usr/sbin/nologin springuser

COPY --from=build --chown=springuser:springgroup \
     /app/order-service/target/*.jar app.jar

RUN chmod 440 app.jar

USER springuser

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseContainerSupport", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dlog4j2.formatMsgNoLookups=true", \
    "-jar", "app.jar"]