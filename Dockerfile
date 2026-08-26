# ---- 1단계: 빌드 스테이지 ----
# JDK가 포함된 이미지로 소스를 컴파일하고 jar를 만든다.
# 이 스테이지의 결과물(build/libs/*.jar)만 다음 스테이지로 넘기고,
# 이 단계 자체(소스코드, Gradle 캐시 등)는 최종 이미지에 남지 않는다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 관련 파일만 먼저 복사한다.
# 소스코드(src)만 바뀌고 build.gradle.kts가 그대로면
# 아래 dependencies 레이어는 캐시되어 재다운로드하지 않는다.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --version

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- 2단계: 실행 스테이지 ----
# JDK 대신 JRE(실행 전용, 더 가벼움)만 있는 이미지를 사용한다.
FROM eclipse-temurin:21-jre AS run
WORKDIR /app

# root 대신 별도 계정으로 실행 (컨테이너가 뚫려도 root 권한을 주지 않기 위함)
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /app/build/libs/*.jar app.jar

# storage.mode=local일 때 파일이 저장될 디렉터리
RUN mkdir -p /app/uploads && chown -R app:app /app
USER app

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
