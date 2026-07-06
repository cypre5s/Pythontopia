FROM maven:3.9-eclipse-temurin-21 AS builder
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY
ENV HTTP_PROXY=${HTTP_PROXY} HTTPS_PROXY=${HTTPS_PROXY} NO_PROXY=${NO_PROXY}
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY .mvn ./.mvn
COPY src ./src
# Maven 代理按需注入：本机受限网络需要激活 settings 里的 proxy；ECS/直连网络留空。
ARG MAVEN_PROXY_OPTS=""
ARG MAVEN_PROXY_HOST="127.0.0.1"
ARG MAVEN_PROXY_PORT="7892"
RUN --mount=type=cache,target=/root/.m2 \
    cp .mvn/settings.build.xml /tmp/settings.build.xml \
    && if [ -n "$MAVEN_PROXY_OPTS" ]; then \
        sed -i \
          -e "s|<active>false</active>|<active>true</active>|" \
          -e "s|<host>127.0.0.1</host>|<host>${MAVEN_PROXY_HOST}</host>|" \
          -e "s|<port>7892</port>|<port>${MAVEN_PROXY_PORT}</port>|" \
          /tmp/settings.build.xml; \
      fi \
    && mvn -B -s /tmp/settings.build.xml \
    ${MAVEN_PROXY_OPTS} \
    -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY
ENV HTTP_PROXY=${HTTP_PROXY} HTTPS_PROXY=${HTTPS_PROXY} NO_PROXY=${NO_PROXY}
# apt 换阿里源：archive.ubuntu.com 经本地代理拉大包（ffmpeg 依赖链）稳定 502。
RUN sed -i 's|http://archive.ubuntu.com/ubuntu|https://mirrors.aliyun.com/ubuntu|g; s|http://security.ubuntu.com/ubuntu|https://mirrors.aliyun.com/ubuntu|g' \
        /etc/apt/sources.list.d/ubuntu.sources 2>/dev/null \
    || sed -i 's|http://archive.ubuntu.com/ubuntu|https://mirrors.aliyun.com/ubuntu|g; s|http://security.ubuntu.com/ubuntu|https://mirrors.aliyun.com/ubuntu|g' \
        /etc/apt/sources.list
RUN useradd --system --create-home --shell /sbin/nologin pythontopia \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl python3 ffmpeg \
    && rm -rf /var/lib/apt/lists/*
# 构建代理仅供上面 apt 拉取使用；清空运行时代理，避免部署到 ECS 后外部调用走不存在的代理。
ENV HTTP_PROXY="" HTTPS_PROXY="" NO_PROXY="" http_proxy="" https_proxy=""
WORKDIR /app
COPY --from=builder /workspace/target/*.jar /app/app.jar
USER pythontopia
EXPOSE 18080
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=40s \
    CMD curl -fsS http://127.0.0.1:18080/healthz || exit 1
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -Dfile.encoding=UTF-8"
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --server.port=18080"]
