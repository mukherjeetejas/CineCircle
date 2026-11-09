# syntax=docker/dockerfile:1

# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# dependencies first, so they are cached while only sources change
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package && cp target/cinecircle-mcp-*.jar /build/app.jar

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build --chown=app:app /build/app.jar /app/app.jar
USER app
# Sized for a 512 MB instance (Render free).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Xss512k"
# Hosted: each caller sends their own token; never use a server-wide token. Render sets PORT.
ENV MCP_ALLOW_ENV_TOKEN=false
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
