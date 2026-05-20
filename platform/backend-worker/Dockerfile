FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Download Sentry OpenTelemetry agent for distributed tracing instrumentation
RUN mvn dependency:copy \
    -Dartifact=io.sentry:sentry-opentelemetry-agent:8.42.0:jar \
    -DoutputDirectory=/app/agents

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/agents/sentry-opentelemetry-agent-8.42.0.jar sentry-agent.jar
EXPOSE 8081

# SENTRY_AUTO_INIT=false: the Spring Boot starter owns SDK initialization;
# the OTel agent must not attempt a second init.
ENV SENTRY_AUTO_INIT=false

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-javaagent:/app/sentry-agent.jar", \
    "-jar", "app.jar"]
