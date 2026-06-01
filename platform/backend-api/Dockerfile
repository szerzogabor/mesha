FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
ENV SENTRY_SKIP_SOURCE_BUNDLE=true
# maven-dependency-plugin:copy-otel-agent copies opentelemetry-javaagent.jar into target/
# alongside the application JAR — no wget/curl needed.
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/target/opentelemetry-javaagent.jar opentelemetry-javaagent.jar
EXPOSE 10000
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-javaagent:/app/opentelemetry-javaagent.jar", \
    "-jar", "app.jar"]
