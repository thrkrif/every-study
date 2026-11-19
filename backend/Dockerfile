# 백엔드 운영 이미지 (PLAN T-013, ARCHITECTURE 9장). 저장소 루트에서:
#   docker build -t every-study-backend backend
# 설정은 실행할 때 환경변수로만 받는다(이미지에 시크릿을 넣지 않는다). 키 이름은 저장소 루트의 .env.example.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# 의존성을 먼저 받아 두면 소스만 바뀐 빌드는 이 층을 다시 쓴다
COPY gradlew settings.gradle build.gradle gradle.lockfile ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies >/dev/null
COPY src src
# 테스트는 CI 가 돌린다. 여기서는 실행 파일만 만든다
RUN ./gradlew --no-daemon bootJar \
 && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar')" /src/app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home app
WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8090
# 컨테이너 메모리의 절반까지 힙으로 쓴다 (작은 VM 에서 MySQL·프론트와 함께 돈다)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "app.jar"]
