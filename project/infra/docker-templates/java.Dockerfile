# 공통 Java 템플릿. backend-spring/Dockerfile이 이 구조를 따른다
# Stage 1: Gradle 빌드
FROM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /app
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN ./gradlew --version
COPY src ./src
RUN ./gradlew bootJar --no-daemon

# Stage 2: 런타임 (JRE 21)
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN useradd -r -u 1001 spring
COPY --from=builder /app/build/libs/*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
