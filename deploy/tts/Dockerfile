# ZONOS3-Mini CPU 服务（ggml）。多阶段构建，运行期不需要 Python/Rust。
#
# 用法（在仓库根）：
#   docker build -f deploy/tts/Dockerfile -t pythontopia-reborn-tts:local \
#     --build-arg JOBS=8 deploy/tts
#
# 说明：
# - 只用 CPU 后端（服务器没有 GPU）。要 CUDA 另建镜像，不在本轮范围。
# - 模型不进镜像，运行时挂载到 /models（见 deploy/compose.tts.yml）。
# - 共享机：构建会吃满编译线程，所以 JOBS 默认 8，避开别人的负载。
FROM debian:bookworm AS build

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates git cmake build-essential pkg-config curl \
    && rm -rf /var/lib/apt/lists/*

# 文本规范化子模块用 Rust 编译。
RUN curl -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal --default-toolchain stable
ENV PATH=/root/.cargo/bin:$PATH

ARG ZONOS3_REF=main
ARG JOBS=8
RUN git clone --recurse-submodules --shallow-submodules --depth 1 \
      --branch "${ZONOS3_REF}" https://github.com/Zyphra/ZONOS3-Mini.git /src

WORKDIR /src/servers/ggml
RUN cmake -B build -DCMAKE_BUILD_TYPE=Release \
    && cmake --build build -j"${JOBS}"

FROM debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive
# ffmpeg 仅用于把上传的参考音频转码（克隆用）；纯默认音色可省。
RUN apt-get update && apt-get install -y --no-install-recommends \
      libstdc++6 libgomp1 ca-certificates curl ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 10002 --create-home --shell /usr/sbin/nologin zonos

COPY --from=build /src/servers/ggml/build/zonos3-server /usr/local/bin/zonos3-server

WORKDIR /models
USER zonos
ENV ZONOS3_FFMPEG=/usr/bin/ffmpeg

EXPOSE 1919
HEALTHCHECK --interval=20s --timeout=5s --start-period=90s --retries=5 \
  CMD sh -c 'curl -fsS -H "Authorization: Bearer ${ZONOS3_ACCESS_TOKEN}" http://127.0.0.1:1919/v2/capabilities > /dev/null'

# --host 0.0.0.0：容器内监听，靠 Docker 网络隔离 + ZONOS3_ACCESS_TOKEN 保护。
ENTRYPOINT ["/usr/local/bin/zonos3-server"]
CMD ["gguf/backbone-ptq.gguf", \
     "--host", "0.0.0.0", "--port", "1919", \
     "--zac", "gguf/zac-keep2.gguf", \
     "--spk-encoder", "gguf/spk-encoder.gguf", \
     "--tts-default-voices-dir", "gguf/voices"]
