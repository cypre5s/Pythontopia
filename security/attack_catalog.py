"""Deterministic 1,000-case security attack catalog for Pythontopia."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from hashlib import sha256
from typing import Any


@dataclass(frozen=True)
class Assertion:
    kind: str
    values: tuple[str | int, ...] = ()


@dataclass(frozen=True)
class AttackCase:
    case_id: str
    domain: str
    family: str
    variant: str
    name: str
    target: str
    method: str
    path: str
    auth_mode: str
    safety: str
    assertion: Assertion
    headers: tuple[tuple[str, str], ...] = ()
    query: tuple[tuple[str, str], ...] = ()
    body: Any = None
    request_kind: str = "json"
    tags: tuple[str, ...] = ()

    @property
    def fingerprint(self) -> str:
        material = repr(
            (
                self.domain,
                self.family,
                self.variant,
                self.target,
                self.method,
                self.path,
                self.auth_mode,
                self.safety,
                self.assertion,
                self.headers,
                self.query,
                self.body,
                self.request_kind,
                self.tags,
            )
        )
        return sha256(material.encode("utf-8")).hexdigest()

    def to_dict(self) -> dict[str, Any]:
        value = asdict(self)
        value["fingerprint"] = self.fingerprint
        return value


PAYLOAD_VARIANTS: tuple[tuple[str, str], ...] = (
    ("plain", "security-canary"),
    ("quote", "'\"`security-canary"),
    ("angle", "<script>security-canary</script>"),
    ("percent", "%253Csecurity-canary%253E"),
    ("unicode", '\"><img src=x onerror=security-canary>'),
    ("nul", "security-canary%00tail"),
    ("crlf", "security-canary%0d%0aX-Audit-Canary:1"),
    ("long", "security-canary-" + "A" * 1024),
    ("array", "security-canary[]"),
    ("object", '{"security":"canary"}'),
)


def _spec(
    family: str,
    target: str,
    method: str,
    path: str,
    auth: str,
    assertion: str,
    *,
    safety: str = "local-active",
    headers: tuple[tuple[str, str], ...] = (),
    query: tuple[tuple[str, str], ...] = (),
    body: Any = None,
    request_kind: str = "json",
    tags: tuple[str, ...] = (),
) -> dict[str, Any]:
    return {
        "family": family,
        "target": target,
        "method": method,
        "path": path,
        "auth": auth,
        "assertion": assertion,
        "safety": safety,
        "headers": headers,
        "query": query,
        "body": body,
        "request_kind": request_kind,
        "tags": tags,
    }


DOMAIN_SPECS: dict[str, tuple[dict[str, Any], ...]] = {
    "authentication": (
        _spec("missing-bearer", "backend", "GET", "/api/learner/profile", "anonymous", "unauthorized"),
        _spec("malformed-bearer", "backend", "GET", "/api/learner/profile", "raw:{{payload}}", "unauthorized"),
        _spec("jwt-bad-signature", "backend", "GET", "/api/learner/profile", "jwt_bad_signature:{{payload}}", "unauthorized"),
        _spec("jwt-none-algorithm", "backend", "GET", "/api/learner/profile", "jwt_none:{{payload}}", "unauthorized"),
        _spec("jwt-expired", "backend", "GET", "/api/learner/profile", "jwt_expired:{{payload}}", "unauthorized"),
        _spec("jwt-subject-tamper", "backend", "GET", "/api/learner/profile", "jwt_subject:{{payload}}", "unauthorized"),
        _spec("jwt-role-tamper", "backend", "GET", "/api/teacher/classrooms/1/dashboard/kc-heatmap", "jwt_role:{{payload}}", "unauthorized"),
        _spec("login-injection", "backend", "POST", "/api/auth/login", "anonymous", "no_5xx", body={"username": "{{payload}}", "password": "wrong-password"}),
        _spec("teacher-self-registration", "backend", "POST", "/api/auth/register", "anonymous", "forbidden_or_validation", body={"username": "audit_teacher_{{index}}", "password": "AuditPass-123!", "displayName": "Audit Teacher", "role": "TEACHER", "email": "audit-teacher-{{index}}@invalid.local", "teacherInviteCode": "{{payload}}"}),
        _spec("refresh-token-tamper", "backend", "POST", "/api/auth/refresh", "anonymous", "unauthorized", body={"token": "{{payload}}"}),
    ),
    "authorization": (
        _spec("anonymous-submission", "backend", "GET", "/api/submissions/1", "anonymous", "unauthorized"),
        _spec("cross-user-submission", "backend", "GET", "/api/submissions/{{student_a_submission_id}}", "student_b", "not_found_or_forbidden"),
        _spec("cross-user-upload", "backend", "GET", "/api/workshop/uploads/{{student_a_upload_id}}", "student_b", "not_found_or_forbidden"),
        _spec("cross-user-upload-delete", "backend", "DELETE", "/api/workshop/uploads/{{student_a_upload_id}}", "student_b", "not_found_or_forbidden", tags=("state-changing",)),
        _spec("student-teacher-dashboard", "backend", "GET", "/api/teacher/classrooms/{{audit_classroom_id}}/dashboard/kc-heatmap", "student_a", "forbidden"),
        _spec("student-teacher-members", "backend", "GET", "/api/teacher/classrooms/{{audit_classroom_id}}/members", "student_a", "forbidden"),
        _spec("student-teacher-resource-usage", "backend", "GET", "/api/teacher/classrooms/{{audit_classroom_id}}/dashboard/resource-usage", "student_a", "forbidden"),
        _spec("cross-classroom-members", "backend", "GET", "/api/classrooms/{{audit_classroom_id}}/members", "student_b", "not_found_or_forbidden"),
        _spec("internal-agent-strategy", "backend", "GET", "/api/internal/agent-strategy/{{payload}}", "student_a", "forbidden"),
        _spec("object-id-shape", "backend", "GET", "/api/learner/evaluations/{{payload}}", "student_a", "no_5xx"),
    ),
    "injection": (
        _spec("problem-search-sqli", "backend", "GET", "/api/course/problems", "anonymous", "no_5xx", query=(("q", "{{payload}}"),)),
        _spec("page-search-sqli", "backend", "GET", "/api/course/pages", "anonymous", "no_5xx", query=(("query", "{{payload}}"),)),
        _spec("submission-filter-sqli", "backend", "GET", "/api/submissions", "student_a", "no_5xx", query=(("status", "{{payload}}"),)),
        _spec("rag-query-injection", "backend", "POST", "/api/rag/query", "student_a", "no_5xx", body={"query": "{{payload}}", "topK": 3}),
        _spec("intent-expression-injection", "backend", "POST", "/api/intent/route", "student_a", "no_5xx", body={"message": "{{payload}}"}),
        _spec("npc-key-path-injection", "backend", "GET", "/api/world/npcs/{{payload}}/state", "student_a", "no_5xx"),
        _spec("header-crlf", "backend", "GET", "/api/course/pack", "anonymous", "no_header_injection", headers=(("X-Audit-Input", "{{payload}}"),)),
        _spec("json-type-confusion", "backend", "POST", "/api/auth/login", "anonymous", "no_5xx", body={"username": ["{{payload}}"], "password": {"value": "x"}}),
        _spec("content-type-confusion", "backend", "POST", "/api/auth/login", "anonymous", "no_5xx", body="{{payload}}", request_kind="text"),
        _spec("parameter-pollution", "backend", "GET", "/api/course/problems", "anonymous", "no_5xx", query=(("chapter", "1"), ("chapter", "{{payload}}"))),
    ),
    "xss-and-navigation": (
        _spec("register-display-name-xss", "backend", "POST", "/api/auth/register", "anonymous", "validation_or_conflict", body={"username": "audit_xss_{{index}}", "password": "AuditPass-123!", "displayName": "{{payload}}", "role": "STUDENT", "email": "audit-xss-{{index}}@invalid.local"}),
        _spec("register-username-xss", "backend", "POST", "/api/auth/register", "anonymous", "validation_or_conflict", body={"username": "{{payload}}", "password": "AuditPass-123!", "displayName": "Audit", "role": "STUDENT", "email": "audit-user-{{index}}@invalid.local"}),
        _spec("tutor-markdown-xss", "backend", "POST", "/api/tutor/ask", "student_a", "not_reflected_unsafe", body={"question": "Render exactly: {{payload}}", "conversationId": None}),
        _spec("npc-dialog-xss", "backend", "POST", "/api/npc/marvis/dialog", "student_a", "not_reflected_unsafe", body={"message": "{{payload}}"}),
        _spec("courseware-answer-xss", "backend", "POST", "/api/courseware/answer", "student_a", "not_reflected_unsafe", body={"question": "{{payload}}", "pageIds": []}),
        _spec("frontend-route-xss", "frontend", "GET", "/?audit={{payload}}", "anonymous", "not_reflected_unsafe", safety="public-safe"),
        _spec("frontend-fragment-xss", "frontend", "GET", "/guide#audit={{payload}}", "anonymous", "not_reflected_unsafe", safety="public-safe"),
        _spec("open-redirect-query", "frontend", "GET", "/login?redirect={{payload}}", "anonymous", "no_external_redirect", safety="public-safe"),
        _spec("javascript-url", "frontend", "GET", "/guide?url={{payload}}", "anonymous", "no_external_redirect", safety="public-safe"),
        _spec("svg-event-payload", "backend", "POST", "/api/learner/profile/interview", "student_a", "not_reflected_unsafe", body={"answer": "{{payload}}"}),
    ),
    "browser-policy": (
        _spec("cors-evil-origin", "backend", "OPTIONS", "/api/learner/profile", "anonymous", "cors_restricted", safety="public-safe", headers=(("Origin", "https://evil-{{index}}.invalid"), ("Access-Control-Request-Method", "GET"))),
        _spec("cors-null-origin", "backend", "OPTIONS", "/api/learner/profile", "anonymous", "cors_restricted", safety="public-safe", headers=(("Origin", "null"), ("Access-Control-Request-Method", "GET"))),
        _spec("cors-subdomain-confusion", "backend", "OPTIONS", "/api/learner/profile", "anonymous", "cors_restricted", safety="public-safe", headers=(("Origin", "http://localhost.evil-{{index}}.invalid"), ("Access-Control-Request-Method", "GET"))),
        _spec("cors-credentialed-origin", "backend", "OPTIONS", "/api/auth/login", "anonymous", "cors_restricted", safety="public-safe", headers=(("Origin", "https://evil-{{index}}.invalid"), ("Access-Control-Request-Method", "POST"))),
        _spec("frontend-csp", "frontend", "GET", "/", "anonymous", "header_csp", safety="public-safe"),
        _spec("frontend-frame-policy", "frontend", "GET", "/", "anonymous", "header_frame", safety="public-safe"),
        _spec("frontend-content-type-policy", "frontend", "GET", "/", "anonymous", "header_nosniff", safety="public-safe"),
        _spec("frontend-referrer-policy", "frontend", "GET", "/", "anonymous", "header_referrer", safety="public-safe"),
        _spec("backend-cache-policy", "backend", "GET", "/api/learner/profile", "student_a", "private_cache_policy"),
        _spec("csrf-state-change", "backend", "POST", "/api/world/sleep", "anonymous", "unauthorized", headers=(("Origin", "https://evil-{{index}}.invalid"),), tags=("state-changing",)),
    ),
    "upload-and-parser": (
        _spec("upload-extension-bypass", "backend", "POST", "/api/workshop/uploads", "student_a", "upload_rejected_or_safe", body={"filename": "audit.{{payload}}.exe", "content": "safe"}, request_kind="multipart"),
        _spec("upload-double-extension", "backend", "POST", "/api/workshop/uploads", "student_a", "upload_rejected_or_safe", body={"filename": "audit.py.{{payload}}", "content": "print('safe')"}, request_kind="multipart"),
        _spec("upload-path-traversal", "backend", "POST", "/api/workshop/uploads", "student_a", "upload_rejected_or_safe", body={"filename": "../{{payload}}.txt", "content": "safe"}, request_kind="multipart"),
        _spec("upload-windows-traversal", "backend", "POST", "/api/workshop/uploads", "student_a", "upload_rejected_or_safe", body={"filename": "..\\{{payload}}.txt", "content": "safe"}, request_kind="multipart"),
        _spec("upload-mime-mismatch", "backend", "POST", "/api/workshop/uploads", "student_a", "upload_rejected_or_safe", body={"filename": "audit.pdf", "content": "{{payload}}", "content_type": "application/pdf"}, request_kind="multipart"),
        _spec("upload-empty-parser", "backend", "POST", "/api/workshop/uploads", "student_a", "no_5xx", body={"filename": "audit.txt", "content": ""}, request_kind="multipart"),
        _spec("upload-oversized-name", "backend", "POST", "/api/workshop/uploads", "student_a", "no_5xx", body={"filename": "{{payload}}.txt", "content": "safe"}, request_kind="multipart"),
        _spec("upload-form-field-confusion", "backend", "POST", "/api/workshop/uploads", "student_a", "no_5xx", body={"filename": "audit.txt", "content": "{{payload}}", "field": "{{payload}}"}, request_kind="multipart"),
        _spec("media-asr-mime", "backend", "POST", "/api/media/asr", "student_a", "no_5xx", body={"filename": "audit.{{payload}}", "content": "safe"}, request_kind="multipart"),
        _spec("upload-ownership-read", "backend", "GET", "/api/workshop/uploads/{{payload}}", "student_b", "no_5xx"),
    ),
    "judge-sandbox": (
        _spec("judge-network", "backend", "POST", "/api/submissions", "student_a", "sandbox_contained", body={"problemId": "{{problem_id}}", "language": "PYTHON", "code": "import socket\nprint(socket.gethostbyname('localhost')) # {{payload}}"}, tags=("sandbox-execution",)),
        _spec("judge-filesystem-read", "backend", "POST", "/api/submissions", "student_a", "sandbox_contained", body={"problemId": "{{problem_id}}", "language": "PYTHON", "code": "print(open('/etc/passwd').read()[:40]) # {{payload}}"}, tags=("sandbox-execution",)),
        _spec("judge-process-spawn", "backend", "POST", "/api/submissions", "student_a", "sandbox_contained", body={"problemId": "{{problem_id}}", "language": "PYTHON", "code": "import subprocess\nprint(subprocess.run(['id'],capture_output=True,text=True).stdout) # {{payload}}"}, tags=("sandbox-execution",)),
        _spec("judge-env-read", "backend", "POST", "/api/submissions", "student_a", "sandbox_contained", body={"problemId": "{{problem_id}}", "language": "PYTHON", "code": "import os\nprint(sorted(os.environ)) # {{payload}}"}, tags=("sandbox-execution",)),
        _spec("judge-fork-pressure", "backend", "POST", "/api/submissions", "student_a", "sandbox_contained", body={"problemId": "{{problem_id}}", "language": "PYTHON", "code": "# bounded fork canary {{payload}}\nprint('no fork executed')"}, tags=("sandbox-execution", "resource-exhaustion")),
        _spec("judge-memory-pressure", "backend", "POST", "/api/submissions", "student_a", "sandbox_contained", body={"problemId": "{{problem_id}}", "language": "PYTHON", "code": "# bounded memory canary {{payload}}\nprint('no allocation executed')"}, tags=("sandbox-execution", "resource-exhaustion")),
        _spec("judge-output-pressure", "backend", "POST", "/api/submissions", "student_a", "sandbox_contained", body={"problemId": "{{problem_id}}", "language": "PYTHON", "code": "print('{{payload}}')"}, tags=("sandbox-execution",)),
        _spec("judge-import-obfuscation", "backend", "POST", "/api/submissions", "student_a", "sandbox_contained", body={"problemId": "{{problem_id}}", "language": "PYTHON", "code": "m=__import__('o'+'s')\nprint(m.getcwd()) # {{payload}}"}, tags=("sandbox-execution",)),
        _spec("judge-dunder-access", "backend", "POST", "/api/submissions", "student_a", "sandbox_contained", body={"problemId": "{{problem_id}}", "language": "PYTHON", "code": "print((1).__class__.__mro__) # {{payload}}"}, tags=("sandbox-execution",)),
        _spec("judge-syntax-parser", "backend", "POST", "/api/submissions", "student_a", "no_5xx", body={"problemId": "{{problem_id}}", "language": "PYTHON", "code": "def broken(:\n  {{payload}}"}, tags=("sandbox-execution",)),
    ),
    "internal-services": (
        _spec("graph-missing-token", "graph", "POST", "/tutor/ask", "anonymous", "unauthorized_or_unconfigured", body={"question": "{{payload}}"}),
        _spec("graph-wrong-token", "graph", "POST", "/tutor/ask", "internal_wrong:{{payload}}", "unauthorized", body={"question": "audit"}),
        _spec("graph-token-timing-shape", "graph", "POST", "/orchestrator/run", "internal_wrong:{{payload}}", "unauthorized", body={"message": "audit"}),
        _spec("graph-openapi-public", "graph", "GET", "/openapi.json", "anonymous", "no_secrets", safety="public-safe"),
        _spec("graph-error-disclosure", "graph", "POST", "/tutor/ask", "internal", "no_stacktrace", body={"question": "{{payload}}", "unexpected": {"x": 1}}),
        _spec("rag-missing-admin-token", "rag", "POST", "/rag/index/rebuild", "anonymous", "unauthorized", body={}),
        _spec("rag-wrong-admin-token", "rag", "POST", "/rag/index/rebuild", "internal_wrong:{{payload}}", "unauthorized", body={}),
        _spec("rag-query-injection", "rag", "POST", "/rag/query", "anonymous", "no_5xx", body={"query": "{{payload}}", "top_k": 3}),
        _spec("rag-openapi-public", "rag", "GET", "/openapi.json", "anonymous", "no_secrets", safety="public-safe"),
        _spec("service-method-confusion", "graph", "GET", "/tutor/ask?question={{payload}}", "internal", "method_rejected"),
    ),
    "http-protocol": (
        _spec("unsupported-method", "backend", "TRACE", "/api/auth/login", "anonymous", "method_rejected"),
        _spec("method-override", "backend", "POST", "/api/learner/profile", "student_a", "method_rejected", headers=(("X-HTTP-Method-Override", "{{payload}}"),)),
        _spec("invalid-content-type", "backend", "POST", "/api/auth/login", "anonymous", "no_5xx", headers=(("Content-Type", "{{payload}}"),), body="{}", request_kind="raw"),
        _spec("duplicate-content-length-canary", "backend", "POST", "/api/auth/login", "anonymous", "no_5xx", headers=(("X-Audit-Content-Length", "{{payload}}"),), body={"username": "x", "password": "x"}),
        _spec("host-header", "frontend", "GET", "/", "anonymous", "no_external_redirect", safety="public-safe", headers=(("X-Forwarded-Host", "evil-{{index}}.invalid"),)),
        _spec("forwarded-proto", "frontend", "GET", "/", "anonymous", "no_external_redirect", safety="public-safe", headers=(("X-Forwarded-Proto", "{{payload}}"),)),
        _spec("accept-confusion", "backend", "GET", "/api/course/pack", "anonymous", "no_5xx", headers=(("Accept", "{{payload}}"),)),
        _spec("range-confusion", "frontend", "GET", "/", "anonymous", "no_5xx", safety="public-safe", headers=(("Range", "bytes={{index}}-{{payload}}"),)),
        _spec("path-normalization", "backend", "GET", "/api/course/../course/pack?audit={{payload}}", "anonymous", "no_5xx"),
        _spec("encoded-path-separator", "backend", "GET", "/api/course%2fpack?audit={{payload}}", "anonymous", "no_5xx"),
    ),
    "deployment-and-secrets": (
        _spec("frontend-dotenv", "frontend", "GET", "/.env?audit={{payload}}", "anonymous", "no_secret_file", safety="public-safe"),
        _spec("frontend-git-config", "frontend", "GET", "/.git/config?audit={{payload}}", "anonymous", "no_secret_file", safety="public-safe"),
        _spec("frontend-source-map", "frontend", "GET", "/assets/index.js.map?audit={{payload}}", "anonymous", "no_source_map", safety="public-safe"),
        _spec("backend-actuator", "backend", "GET", "/actuator/env?audit={{payload}}", "anonymous", "not_found_or_unauthorized", safety="public-safe"),
        _spec("backend-swagger", "backend", "GET", "/v3/api-docs?audit={{payload}}", "anonymous", "production_docs_restricted", safety="public-safe"),
        _spec("backend-error-page", "backend", "GET", "/api/not-found-{{index}}?audit={{payload}}", "anonymous", "no_stacktrace", safety="public-safe"),
        _spec("frontend-server-version", "frontend", "HEAD", "/?audit={{payload}}", "anonymous", "no_detailed_server_version", safety="public-safe"),
        _spec("backend-server-version", "backend", "HEAD", "/healthz?audit={{payload}}", "anonymous", "no_detailed_server_version", safety="public-safe"),
        _spec("public-media-id-enumeration", "backend", "GET", "/api/media/assets/{{index}}?audit={{payload}}", "anonymous", "no_5xx", safety="public-safe"),
        _spec("health-information", "backend", "GET", "/healthz?audit={{payload}}", "anonymous", "minimal_health", safety="public-safe"),
    ),
}


def _replace(value: Any, payload: str, index: int) -> Any:
    if isinstance(value, str):
        return value.replace("{{payload}}", payload).replace("{{index}}", str(index))
    if isinstance(value, dict):
        return {key: _replace(item, payload, index) for key, item in value.items()}
    if isinstance(value, tuple):
        return tuple(_replace(item, payload, index) for item in value)
    if isinstance(value, list):
        return [_replace(item, payload, index) for item in value]
    return value


def build_attack_catalog() -> list[AttackCase]:
    cases: list[AttackCase] = []
    for domain_index, (domain, specs) in enumerate(DOMAIN_SPECS.items(), start=1):
        if len(specs) != 10:
            raise ValueError(f"domain {domain} must define exactly 10 families")
        for family_index, spec in enumerate(specs, start=1):
            for variant_index, (variant, payload) in enumerate(PAYLOAD_VARIANTS, start=1):
                assertion_parts = spec["assertion"].split(":", 1)
                assertion = Assertion(
                    assertion_parts[0],
                    tuple(assertion_parts[1].split(",")) if len(assertion_parts) == 2 else (),
                )
                case_id = f"SEC-{domain_index:02d}-{family_index:02d}-{variant_index:02d}"
                cases.append(
                    AttackCase(
                        case_id=case_id,
                        domain=domain,
                        family=spec["family"],
                        variant=variant,
                        name=f"{spec['family']} / {variant}",
                        target=spec["target"],
                        method=spec["method"],
                        path=_replace(spec["path"], payload, variant_index),
                        auth_mode=_replace(spec["auth"], payload, variant_index),
                        safety=spec["safety"],
                        assertion=assertion,
                        headers=_replace(spec["headers"], payload, variant_index),
                        query=_replace(spec["query"], payload, variant_index),
                        body=_replace(spec["body"], payload, variant_index),
                        request_kind=spec["request_kind"],
                        tags=spec["tags"],
                    )
                )
    return cases


if __name__ == "__main__":
    import json

    for attack_case in build_attack_catalog():
        print(json.dumps(attack_case.to_dict(), ensure_ascii=True, sort_keys=True))
