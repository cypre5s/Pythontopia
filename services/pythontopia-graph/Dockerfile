FROM python:3.11-slim
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml ./
COPY pythontopia_graph ./pythontopia_graph
RUN pip install --no-cache-dir .

EXPOSE 18091
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=15s \
    CMD curl -fsS http://127.0.0.1:18091/healthz || exit 1
CMD ["uvicorn", "pythontopia_graph.main:app", "--host", "0.0.0.0", "--port", "18091", "--workers", "1"]
