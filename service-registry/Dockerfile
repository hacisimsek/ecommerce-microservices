# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /app

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# ✅ Copy ROOT pom.xml only (root has no .mvn folder — removed that line)
COPY pom.xml ./pom.xml

# ✅ Use the service's own Maven wrapper (.mvn lives inside service-registry/)
COPY service-registry/.mvn/ .mvn/
COPY service-registry/mvnw ./
RUN chmod +x mvnw

# ✅ Install parent POM into local repo so child module can resolve it
RUN ./mvnw install -N -B --no-transfer-progress

# ✅ Copy service pom.xml separately for Docker layer caching
COPY service-registry/pom.xml ./

# Download dependencies
RUN ./mvnw dependency:go-offline -B --no-transfer-progress

# Copy source code
COPY service-registry/src ./src

# Build
RUN ./mvnw clean package \
    -DskipTests \
    -B \
    --no-transfer-progress

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN apt-get update && apt-get upgrade -y && rm -rf /var/lib/apt/lists/*
RUN useradd -m springuser
USER springuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseContainerSupport", "-jar", "app.jar"]