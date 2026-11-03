FROM debian:trixie-slim
# Debian-signed security updates supply LibreOffice. Rebuild on security updates;
# record the resulting image digest for a production deployment.
RUN apt-get update && apt-get install -y --no-install-recommends libreoffice-impress python3 fonts-dejavu-core fonts-noto-cjk \
    && dpkg-query -W libreoffice-core > /opt/converter-package-version \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app /work && chown 10001:10001 /app /work
COPY server.py /app/server.py
USER 10001:10001
ENV HOME=/work TMPDIR=/work PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
EXPOSE 8090
CMD ["python3", "server.py"]
