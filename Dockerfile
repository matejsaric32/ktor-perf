FROM gradle:8.14-jdk21 AS builder

WORKDIR /build

COPY gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY gradlew ./

COPY core/build.gradle.kts ./core/
COPY client/build.gradle.kts ./client/
COPY server/build.gradle.kts ./server/

COPY core/src ./core/src
COPY client/src ./client/src
COPY server/src ./server/src

RUN gradle :server:buildFatJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /build/server/build/libs/*-all.jar app.jar

RUN addgroup -g 1001 -S appuser && \
    adduser -u 1001 -S appuser -G appuser && \
    chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]