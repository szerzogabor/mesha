FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Download the standard OpenTelemetry Java Agent for distributed tracing.
# The agent auto-instruments Spring Boot, JDBC, Redis, and HTTP clients and exports
# spans to Grafana Tempo (or any OTLP backend) via OTEL_EXPORTER_OTLP_ENDPOINT.
RUN mvn dependency:copy \
    -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:2.12.0:jar \
    -DoutputDirectory=/app/agents

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/agents/opentelemetry-javaagent-2.12.0.jar otel-agent.jar
EXPOSE 8081

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-javaagent:/app/otel-agent.jar", \
    "-jar", "app.jar"]
