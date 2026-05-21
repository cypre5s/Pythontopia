FROM python:3.11-slim
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml ./
COPY pythontopia_rag ./pythontopia_rag
RUN pip install --no-cache-dir .

EXPOSE 18092
HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=15s \
    CMD curl -fsS http://127.0.0.1:18092/healthz || exit 1
CMD ["uvicorn", "pythontopia_rag.main:app", "--host", "0.0.0.0", "--port", "18092", "--workers", "1"]
