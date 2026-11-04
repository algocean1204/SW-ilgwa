# SW-EZ-Team backend-spring 서비스 컨테이너
# 스테이지 1 builder: JDK 21에서 Gradle로 실행 가능한 JAR를 빌드한다
# eclipse-temurin:noble은 Ubuntu 24.04 기반 glibc 이미지다
FROM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /app

# Gradle wrapper와 빌드 스크립트를 먼저 복사해서 의존성 레이어를 캐시한다
# 소스만 바뀌면 의존성을 다시 받지 않는다
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version

# 소스 코드를 복사하고 실행 가능한 fat JAR를 생성한다
# 테스트는 CI/CD에서 별도로 실행하므로 빌드 시에는 제외한다
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# 스테이지 2 runtime: JRE만 든 최소 이미지에서 JAR를 실행한다
# eclipse-temurin:jammy는 Ubuntu 22.04 기반 glibc 이미지다
# 컴파일러가 없는 JRE만 써서 이미지 크기와 공격 표면을 줄인다
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# 헬스체크와 운영 로그 시간대를 위해 최소 패키지만 설치한다
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && ln -sf /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 spring

COPY --from=builder /app/build/libs/app.jar app.jar
RUN chown -R spring:spring /app
USER spring

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/app.jar"]
