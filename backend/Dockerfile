FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY .mvn ./.mvn
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
RUN useradd --system --create-home --shell /sbin/nologin pythontopia \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl python3-minimal \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /workspace/target/*.jar /app/app.jar
USER pythontopia
EXPOSE 18080
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=40s \
    CMD curl -fsS http://127.0.0.1:18080/healthz || exit 1
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -Dfile.encoding=UTF-8"
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --server.port=18080"]
