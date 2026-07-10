# --- Build stage ---
FROM eclipse-temurin:11-jdk-jammy AS builder
WORKDIR /app

# Cache dependency resolution separately from source changes.
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# --- Runtime stage ---
FROM eclipse-temurin:11-jre-jammy
WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin appuser
COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown appuser:appuser app.jar
USER appuser

ENV SPRING_PROFILES_ACTIVE=render
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
