FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package \
    && mv target/forms-*.jar target/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget \
    && addgroup -S -g 10001 forms \
    && adduser -S -u 10001 -G forms forms
WORKDIR /app
COPY --from=build --chown=forms:forms /src/target/app.jar app.jar
USER forms
EXPOSE 8094
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=8 \
    CMD wget -qO- http://127.0.0.1:8094/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
