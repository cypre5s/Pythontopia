#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_TOKEN="$(printf 'a%.0s' {1..64})"

python3 -c '
from pathlib import Path
import re, sys

root = Path(sys.argv[1])
frontend = (root / "frontend/nginx.conf").read_text()
edge = (root / "deploy/nginx/pythontopia.conf").read_text()
index_location = re.search(r"location = /index\.html \{(?P<body>.*?)\n    \}", frontend, re.S)
assert index_location, "missing exact index.html response block"
worker_location = re.search(
    re.escape("location ~* ^/assets/pyodide\\.worker-[^/]+\\.js$ {")
    + r"(?P<body>.*?)\n    \}",
    frontend,
    re.S,
)
assert worker_location, "Pyodide eval must be isolated to its worker response"
policy = re.search(
    r"add_header Content-Security-Policy \"(?P<policy>[^\"]+)\" always;",
    index_location.group("body"),
)
assert policy, "the actual SPA document response must carry an enforced CSP"
directives = dict(
    item.strip().split(None, 1) if " " in item.strip() else (item.strip(), "")
    for item in policy.group("policy").split(";") if item.strip()
)
assert set(directives) == {
    "default-src", "base-uri", "object-src", "form-action", "frame-ancestors",
    "script-src", "style-src", "img-src", "font-src", "connect-src", "worker-src",
    "frame-src", "media-src", "manifest-src",
}
assert directives.get("default-src") == "\u0027self\u0027"
assert directives.get("script-src") == "\u0027self\u0027 \u0027wasm-unsafe-eval\u0027"
assert directives.get("style-src") == "\u0027self\u0027 \u0027unsafe-inline\u0027"
assert directives.get("object-src") == "\u0027none\u0027"
assert directives.get("base-uri") == "\u0027self\u0027"
assert directives.get("form-action") == "\u0027self\u0027"
assert directives.get("frame-ancestors") == "\u0027self\u0027"
assert directives.get("img-src") == "\u0027self\u0027 data: blob: https://*.hdslb.com"
assert directives.get("font-src") == "\u0027self\u0027"
assert directives.get("connect-src") == "\u0027self\u0027"
assert directives.get("worker-src") == "\u0027self\u0027"
assert directives.get("frame-src") == "\u0027none\u0027"
assert directives.get("media-src") == "\u0027self\u0027"
assert directives.get("manifest-src") == "\u0027self\u0027"
assert "\u0027unsafe-eval\u0027" not in policy.group("policy")
worker_policy = re.search(
    r"add_header Content-Security-Policy \"(?P<policy>[^\"]+)\" always;",
    worker_location.group("body"),
)
assert worker_policy, "the Pyodide worker must carry its own CSP"
assert "script-src \u0027self\u0027 \u0027unsafe-eval\u0027 \u0027wasm-unsafe-eval\u0027" in worker_policy.group("policy")
assert "connect-src \u0027self\u0027" in worker_policy.group("policy")
assert "add_header X-Frame-Options \"SAMEORIGIN\" always;" in edge
assert "add_header Content-Security-Policy" not in edge
print("CSP document-response contract: PASS")
' "$ROOT_DIR"

python3 -c '
from pathlib import Path
import sys
script = (Path(sys.argv[1]) / "dev.sh").read_text()
guard = script.index("if [[ \"$START_DEPS\" == \"0\" && \"$resolved_internal_token\" != \"$configured_internal_token\" ]]")
export = script.index("export PYT_RAG_ADMIN_TOKEN=")
compose = script.index("compose up -d \"${DEP_SERVICES[@]}\"")
reuse = script.index("backend_preexisting=0")
assert reuse < guard < export < compose
assert "resolve_dev_internal_token.sh\" \"$configured_internal_token\" \"$existing_backend_pid\"" in script
print("dev startup token order: PASS")
' "$ROOT_DIR"

PYT_JWT_SECRET="test-jwt-secret-for-compose-config-validation-123456" \
PYT_TEACHER_INVITE_CODE="test-teacher-invite-code" \
PYT_RAG_ADMIN_TOKEN="$TEST_TOKEN" \
  docker compose --profile sandbox --project-directory "$ROOT_DIR/deploy" \
    -f "$ROOT_DIR/deploy/docker-compose.yml" config --format json |
  python3 -c '
import json, sys
services = json.load(sys.stdin)["services"]

def assert_loopback_port(service, port):
    bindings = [binding for binding in services[service].get("ports", [])
                if str(binding.get("published")) == str(port)]
    assert bindings and all(binding.get("host_ip") == "127.0.0.1" for binding in bindings), service

for service, port in (("postgres", 15432), ("redis", 16379), ("backend", 18080),
                      ("pythontopia-rag", 18092), ("frontend", 8080), ("piston", 2000)):
    assert_loopback_port(service, port)

piston = services["piston"]
assert not piston.get("privileged", False)
assert piston["read_only"] is True
assert set(piston["cap_drop"]) == {"ALL"}
assert set(piston["cap_add"]) == {"CHOWN", "DAC_OVERRIDE", "SETGID", "SETUID", "KILL"}
assert "no-new-privileges:true" in piston["security_opt"]
assert piston["mem_limit"] == piston["memswap_limit"] == "805306368"
assert piston["cpus"] == 1 and piston["pids_limit"] == 128
assert "/piston/jobs:exec,size=256m" in piston["tmpfs"]
assert "/tmp:exec,size=256m,mode=700" in piston["tmpfs"]
assert piston["environment"]["PISTON_RUN_TIMEOUT"] == "10000"
assert piston["environment"]["PISTON_RUN_MEMORY_LIMIT"] == "268435456"
assert piston["environment"]["PISTON_MAX_CONCURRENT_JOBS"] == "2"
assert piston["environment"]["PISTON_DISABLE_NETWORKING"] == "true"

graph = services["pythontopia-graph"]
assert graph["network_mode"] == "host"
assert graph["command"][2:4] == ["--host", "127.0.0.1"]
assert "--reload" in graph["command"]
reload_dir_index = graph["command"].index("--reload-dir")
assert graph["command"][reload_dir_index + 1] == "/app/pythontopia_graph"
assert "--workers" not in graph["command"]
assert not graph.get("ports")
print("dev compose security contract: PASS")
'
