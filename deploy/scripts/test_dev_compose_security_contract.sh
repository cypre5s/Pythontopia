#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_TOKEN="$(printf 'a%.0s' {1..64})"

PYT_JWT_SECRET="test-jwt-secret-for-compose-config-validation-123456" \
PYT_TEACHER_INVITE_CODE="test-teacher-invite-code" \
PYT_RAG_ADMIN_TOKEN="$TEST_TOKEN" \
  docker compose --project-directory "$ROOT_DIR/deploy" \
    -f "$ROOT_DIR/deploy/docker-compose.yml" config --format json |
  python3 -c '
import json, sys
services = json.load(sys.stdin)["services"]

def assert_loopback_port(service, port):
    bindings = [binding for binding in services[service].get("ports", [])
                if str(binding.get("published")) == str(port)]
    assert bindings and all(binding.get("host_ip") == "127.0.0.1" for binding in bindings), service

for service, port in (("postgres", 15432), ("redis", 16379), ("backend", 18080),
                      ("pythontopia-rag", 18092), ("frontend", 8080)):
    assert_loopback_port(service, port)

graph = services["pythontopia-graph"]
assert graph["network_mode"] == "host"
assert graph["command"][2:4] == ["--host", "127.0.0.1"]
assert not graph.get("ports")
print("dev compose security contract: PASS")
'
