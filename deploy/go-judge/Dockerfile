FROM criyle/go-judge:v1.14.1@sha256:da459bed95efca8f322d98598fed5822379b12bf2a0f83c17b34ad55bca19b30 AS judge
FROM python:3.13-slim@sha256:ae14d4ce8771606477a81446e7ef429edd6762305fc50c84507c1041fa8285d1
WORKDIR /opt
COPY --from=judge /opt/go-judge /opt/go-judge
COPY mount.yaml /opt/mount.yaml
COPY LICENSE /opt/licenses/go-judge-LICENSE
COPY healthcheck.py /opt/healthcheck.py
EXPOSE 5050
ENTRYPOINT ["/opt/go-judge"]
CMD ["-http-addr=:5050", "-parallelism=4", "-no-fallback", "-release", "-output-limit=256k", "-copy-out-limit=64k"]
