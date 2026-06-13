FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# maven-dependency-plugin:copy-otel-agent downloads opentelemetry-javaagent.jar into
# target/agents/ — separate from target/*.jar so the wildcard COPY below matches only
# the single Spring Boot fat JAR.
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/target/agents/opentelemetry-javaagent.jar opentelemetry-javaagent.jar
EXPOSE 10000
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=60.0", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/tmp/mesha-api-heapdump.hprof", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-javaagent:/app/opentelemetry-javaagent.jar", \
    "-jar", "app.jar"]
