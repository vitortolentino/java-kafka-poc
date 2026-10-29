# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-25 AS base
WORKDIR /app

# Development: source code is mounted as a volume (see docker-compose.yaml)
FROM base AS dev
COPY docker/dev-entrypoint.sh /usr/local/bin/dev-entrypoint.sh
RUN chmod +x /usr/local/bin/dev-entrypoint.sh
EXPOSE 8080 5005
CMD ["dev-entrypoint.sh"]

# Jar build (used by CI/CD)
FROM base AS build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q package -DskipTests

# Final image: JRE + jar only
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
