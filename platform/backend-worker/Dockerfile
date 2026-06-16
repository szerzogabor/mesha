FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/target/agents/opentelemetry-javaagent.jar opentelemetry-javaagent.jar
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=55.0", \
    "-Xss512k", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/tmp/mesha-worker-heapdump.hprof", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-javaagent:/app/opentelemetry-javaagent.jar", \
    "-jar", "app.jar"]
