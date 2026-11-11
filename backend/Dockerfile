# Backend image: build with Maven, run on a JRE as a non-root user. The prod profile is active by default and
# refuses to start with unsafe settings (see ProductionSafetyCheck).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home /app medicare && mkdir -p /app/uploads && chown -R medicare /app
WORKDIR /app
COPY --from=build /src/target/medicare-backend-1.0.0.jar app.jar
USER medicare
ENV SPRING_PROFILES_ACTIVE=prod \
    UPLOAD_DIR=/app/uploads
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD curl -fsS http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
