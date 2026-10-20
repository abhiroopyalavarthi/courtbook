# One Dockerfile for every Java service. Build from the repo root:
#   docker build --build-arg SERVICE=booking-service -t courtbook/booking-service .
FROM eclipse-temurin:21-jdk AS build
ARG SERVICE
WORKDIR /src

# Copy build files first so dependency downloads are cached between code changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY gateway/build.gradle gateway/
COPY user-service/build.gradle user-service/
COPY booking-service/build.gradle booking-service/
COPY tournament-service/build.gradle tournament-service/
COPY notification-service/build.gradle notification-service/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :${SERVICE}:dependencies > /dev/null

COPY ${SERVICE}/src ${SERVICE}/src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :${SERVICE}:bootJar -x test \
    && cp ${SERVICE}/build/libs/*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app
USER app
WORKDIR /app
COPY --from=build /app.jar app.jar
# Heap is capped per service so all five fit on one small EC2 instance.
ENV JAVA_OPTS="-Xmx384m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
