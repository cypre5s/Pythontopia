FROM python:3.11-slim
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY
ENV HTTP_PROXY=${HTTP_PROXY} HTTPS_PROXY=${HTTPS_PROXY} NO_PROXY=${NO_PROXY}
WORKDIR /app

# apt 换阿里源：deb.debian.org 在国内网络拉索引极慢（ECS 直连也一样）。
RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml ./
COPY pythontopia_graph ./pythontopia_graph
RUN pip install --no-cache-dir \
    --index-url https://mirrors.aliyun.com/pypi/simple/ \
    --trusted-host mirrors.aliyun.com \
    .

# 构建代理仅供上面 apt/pip 拉取使用；务必清空运行时代理，否则部署到 ECS 后 httpx 会去
# 连不存在的 7892 代理 → LLM/外部调用 ConnectError（AI 功能全线失效）。
ENV HTTP_PROXY="" HTTPS_PROXY="" NO_PROXY="" http_proxy="" https_proxy=""

EXPOSE 18091
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=15s \
    CMD curl -fsS http://127.0.0.1:18091/healthz || exit 1
CMD ["uvicorn", "pythontopia_graph.main:app", "--host", "0.0.0.0", "--port", "18091", "--workers", "1"]
