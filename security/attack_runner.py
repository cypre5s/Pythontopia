"""Allowlisted HTTP runner for the Pythontopia security attack catalog."""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import asdict
from pathlib import Path
from typing import Any

from attack_catalog import Assertion, AttackCase, build_attack_catalog


LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1"}
SECRET_KEYS = {
    "authorization",
    "cookie",
    "password",
    "token",
    "x-internal-token",
    "teacherinvitecode",
}
STACKTRACE_MARKERS = (
    "java.lang.",
    "org.springframework.",
    "traceback (most recent call last)",
    'file "/app/',
    'file "/home/',
)


def select_cases_for_mode(cases: list[AttackCase], execution_mode: str) -> list[AttackCase]:
    if execution_mode in {"local", "public-active"}:
        return list(cases)
    if execution_mode == "public-safe":
        return [case for case in cases if case.safety == "public-safe"]
    raise ValueError(f"unknown execution mode: {execution_mode}")


def validate_target(url: str, mode: str, approved_public_hosts: set[str] | None = None) -> None:
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise ValueError(f"invalid target URL: {url}")
    if mode == "local":
        if parsed.hostname not in LOCAL_HOSTS:
            raise ValueError(f"local mode rejects non-loopback target: {parsed.hostname}")
        return
    if mode == "public":
        approved = approved_public_hosts or set()
        if parsed.hostname not in approved:
            raise ValueError(f"public target is not explicitly approved: {parsed.hostname}")
        return
    raise ValueError(f"unknown mode: {mode}")


def redact_evidence(value: Any, max_body: int = 4096, key: str = "") -> Any:
    normalized_key = key.lower().replace("_", "").replace("-", "")
    if normalized_key in {item.replace("-", "") for item in SECRET_KEYS}:
        return "[REDACTED]"
    if isinstance(value, dict):
        return {item_key: redact_evidence(item, max_body, item_key) for item_key, item in value.items()}
    if isinstance(value, list):
        return [redact_evidence(item, max_body, key) for item in value]
    if isinstance(value, tuple):
        return [redact_evidence(item, max_body, key) for item in value]
    if isinstance(value, str) and key.lower() == "body":
        return value[:max_body]
    return value


def read_response_body(response: Any, max_bytes: int = 16 * 1024) -> str:
    """Read one bounded chunk so SSE/chunked responses never wait for EOF."""
    reader = getattr(response, "read1", None)
    data = reader(max_bytes) if callable(reader) else response.read(max_bytes)
    return data.decode("utf-8", errors="replace")


def normalize_request_url(url: str) -> str:
    """Encode payload characters while preserving URL delimiters and existing escapes."""
    return urllib.parse.quote(url, safe=":/?&=%#[]@!$'()*+,;`~")


def evaluate_assertion(
    assertion: Assertion,
    status: int | None,
    headers: dict[str, str],
    body: str,
    *,
    mode: str = "local",
) -> bool:
    if status is None:
        return False
    headers = {name.lower(): value for name, value in headers.items()}
    kind = assertion.kind
    if kind == "unauthorized":
        return status in {401, 403}
    if kind == "forbidden":
        return status == 403
    if kind == "unauthorized_or_unconfigured":
        return status in {401, 403, 503}
    if kind == "not_found_or_forbidden":
        return status in {403, 404}
    if kind == "not_found_or_unauthorized":
        return status in {401, 403, 404}
    if kind == "forbidden_or_validation":
        return status in {400, 403, 409, 422}
    if kind == "validation_or_conflict":
        return status in {201, 400, 409, 422} and not _contains_unsafe_html(body)
    if kind == "no_5xx":
        return status < 500
    if kind == "no_stacktrace":
        lowered = body.lower()
        return status < 500 and not any(marker in lowered for marker in STACKTRACE_MARKERS)
    if kind == "not_reflected_unsafe":
        return status < 500 and not _contains_unsafe_html(body)
    if kind == "cors_restricted":
        allowed = headers.get("access-control-allow-origin", "")
        return allowed not in {"*", "null"} and "evil-" not in allowed and ".invalid" not in allowed
    if kind == "header_csp":
        return bool(headers.get("content-security-policy"))
    if kind == "header_frame":
        return bool(headers.get("x-frame-options") or "frame-ancestors" in headers.get("content-security-policy", ""))
    if kind == "header_nosniff":
        return headers.get("x-content-type-options", "").lower() == "nosniff"
    if kind == "header_referrer":
        return bool(headers.get("referrer-policy"))
    if kind == "private_cache_policy":
        cache = headers.get("cache-control", "").lower()
        successful = 200 <= status < 300
        return status < 500 and (not successful or "private" in cache or "no-store" in cache)
    if kind == "no_header_injection":
        return "x-audit-canary" not in headers
    if kind == "no_external_redirect":
        location = headers.get("location", "")
        return status < 500 and not (location.startswith("http://") or location.startswith("https://"))
    if kind == "upload_rejected_or_safe":
        return status in {200, 201, 400, 403, 413, 415, 422} and not _contains_unsafe_html(body)
    if kind == "sandbox_contained":
        lowered = body.lower()
        leaked = ("root:x:" in lowered or "uid=" in lowered or "pyt_jwt_secret" in lowered)
        return status < 500 and not leaked
    if kind == "no_secrets":
        lowered = body.lower()
        return status < 500 and not any(token in lowered for token in ("api_key", "api_secret", "jwt_secret", "db_password"))
    if kind == "method_rejected":
        return status in {400, 401, 403, 404, 405, 415, 422}
    if kind == "no_secret_file":
        lowered = body.lower()
        return status in {401, 403, 404} or not any(token in lowered for token in ("pyt_jwt_secret=", "pyt_db_password=", "private key"))
    if kind == "no_source_map":
        return status in {401, 403, 404} or '"sourcescontent"' not in body.lower()
    if kind == "production_docs_restricted":
        return status < 500 if mode == "local" else status in {401, 403, 404}
    if kind == "internal_not_exposed":
        content_type = headers.get("content-type", "").lower()
        lowered = body.lower()
        is_spa_shell = (
            status == 200
            and "text/html" in content_type
            and ("<!doctype html" in lowered or "pythontopia" in lowered)
        )
        return status in {401, 403, 404, 405} or is_spa_shell
    if kind == "no_detailed_server_version":
        server = headers.get("server", "")
        return not bool(re.search(r"\d+\.\d+", server))
    if kind == "minimal_health":
        lowered = body.lower()
        return status == 200 and not any(token in lowered for token in ("password", "jdbc:", "secret", "stacktrace"))
    raise ValueError(f"unknown assertion kind: {kind}")


def _contains_unsafe_html(body: str) -> bool:
    lowered = body.lower()
    if "security-canary" not in lowered:
        return False
    return any(
        token in lowered
        for token in ("<script", "javascript:", " onerror=", " onload=", "<iframe")
    )


def _load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        os.environ.setdefault(name.strip(), value.strip().strip('"').strip("'"))


def _b64url_decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def _b64url_encode(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _tamper_jwt(token: str, mode: str, marker: str) -> str:
    parts = token.split(".")
    if len(parts) != 3:
        return marker
    if mode == "jwt_bad_signature":
        signature = parts[2]
        replacement = "A" if not signature.endswith("A") else "B"
        return ".".join((parts[0], parts[1], signature[:-1] + replacement))
    try:
        header = json.loads(_b64url_decode(parts[0]))
        payload = json.loads(_b64url_decode(parts[1]))
    except (ValueError, json.JSONDecodeError):
        return marker
    if mode == "jwt_none":
        header["alg"] = "none"
        return f"{_b64url_encode(json.dumps(header).encode())}.{parts[1]}."
    if mode == "jwt_expired":
        payload["exp"] = 1
    elif mode == "jwt_subject":
        payload["sub"] = marker
        payload["userId"] = 1
    elif mode == "jwt_role":
        payload["role"] = "TEACHER"
    return f"{parts[0]}.{_b64url_encode(json.dumps(payload, separators=(',', ':')).encode())}.{parts[2]}"


class AuditRunner:
    def __init__(
        self,
        mode: str,
        base_urls: dict[str, str],
        approved_public_hosts: set[str] | None = None,
        timeout: float = 8.0,
        delay: float = 0.01,
    ) -> None:
        self.mode = mode
        self.base_urls = base_urls
        self.timeout = timeout
        self.delay = delay
        self.approved_public_hosts = approved_public_hosts or set()
        for base_url in base_urls.values():
            validate_target(base_url, mode, self.approved_public_hosts)
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        self.context: dict[str, Any] = {
            "student_a_submission_id": 1,
            "student_a_upload_id": 1,
            "audit_classroom_id": 1,
            "problem_id": 1,
        }
        self.tokens: dict[str, str] = {}
        self.internal_token = os.environ.get("PYT_RAG_ADMIN_TOKEN", "")

    def bootstrap_audit_accounts(self) -> None:
        suffix = f"{int(time.time())}{secrets.randbelow(10000):04d}"
        password = "AuditPass-123!"
        accounts = {
            "student_a": (f"audit_sa_{suffix}", "STUDENT", ""),
            "student_b": (f"audit_sb_{suffix}", "STUDENT", ""),
        }
        if self.mode == "local" and os.environ.get("PYT_TEACHER_INVITE_CODE", ""):
            accounts["teacher"] = (
                f"audit_t_{suffix}",
                "TEACHER",
                os.environ.get("PYT_TEACHER_INVITE_CODE", ""),
            )
        for key, (username, role, invite) in accounts.items():
            register_body = {
                "username": username,
                "password": password,
                "displayName": f"Security Audit {key}",
                "role": role,
                "email": f"{username}@invalid.local",
            }
            if role == "TEACHER":
                register_body["teacherInviteCode"] = invite
            self._raw_request("backend", "POST", "/api/auth/register", body=register_body)
            status, _, body, _ = self._raw_request(
                "backend",
                "POST",
                "/api/auth/login",
                body={"username": username, "password": password},
            )
            if status == 200:
                try:
                    self.tokens[key] = json.loads(body)["token"]
                except (KeyError, json.JSONDecodeError):
                    pass
        missing = [key for key in ("student_a", "student_b") if not self.tokens.get(key)]
        if missing:
            raise RuntimeError(
                "audit account bootstrap failed for " + ", ".join(missing)
            )
        # Public deployments intentionally do not expose the teacher invite secret. Reuse a
        # student token for negative role checks so teacher-only endpoints are still attacked.
        self.tokens.setdefault("teacher", self.tokens.get("student_a", ""))
        self._bootstrap_problem()
        self._bootstrap_owned_objects()

    def _bootstrap_problem(self) -> None:
        status, _, body, _ = self._raw_request("backend", "GET", "/api/course/problems?limit=1")
        if status != 200:
            return
        try:
            value = json.loads(body)
            items = value if isinstance(value, list) else value.get("items", value.get("content", []))
            if items:
                self.context["problem_id"] = items[0]["id"]
        except (KeyError, TypeError, json.JSONDecodeError):
            return

    def _bootstrap_owned_objects(self) -> None:
        if self.tokens.get("student_a"):
            status, _, body, _ = self._raw_request(
                "backend",
                "POST",
                "/api/submissions",
                auth_token=self.tokens["student_a"],
                body={
                    "problemId": self.context["problem_id"],
                    "code": "print(1)",
                    "source": "TRAINING",
                },
            )
            self._capture_id(status, body, "student_a_submission_id")
            multipart, content_type = self._multipart(
                {"filename": "security-audit.txt", "content": "security audit ownership canary"}
            )
            status, _, body, _ = self._raw_request(
                "backend",
                "POST",
                "/api/workshop/uploads",
                auth_token=self.tokens["student_a"],
                raw_body=multipart,
                headers={"Content-Type": content_type},
            )
            self._capture_id(status, body, "student_a_upload_id")
        if self.tokens.get("teacher"):
            status, _, body, _ = self._raw_request(
                "backend",
                "POST",
                "/api/classrooms",
                auth_token=self.tokens["teacher"],
                body={"name": "Security Audit Classroom", "description": "Disposable local audit data"},
            )
            self._capture_id(status, body, "audit_classroom_id")

    def _capture_id(self, status: int | None, body: str, key: str) -> None:
        if status not in {200, 201}:
            return
        try:
            identifier = json.loads(body).get("id")
            if identifier is not None:
                self.context[key] = identifier
        except (AttributeError, json.JSONDecodeError):
            pass

    def run_case(self, case: AttackCase) -> dict[str, Any]:
        if case.target not in self.base_urls:
            return self._result(case, "not_applicable", None, {}, "", "target not exposed")
        path = self._render(case.path)
        headers = {name: self._render(value) for name, value in case.headers}
        query = [(name, self._render(value)) for name, value in case.query]
        if query:
            separator = "&" if "?" in path else "?"
            path += separator + urllib.parse.urlencode(query, doseq=True)
        body = self._render_value(case.body)
        auth_token, internal_token = self._resolve_auth(case.auth_mode)
        raw_body: bytes | None = None
        if case.request_kind == "multipart":
            raw_body, content_type = self._multipart(body or {})
            headers["Content-Type"] = content_type
            body = None
        elif case.request_kind == "text":
            raw_body = str(body or "").encode("utf-8")
            headers.setdefault("Content-Type", "text/plain")
            body = None
        elif case.request_kind == "raw":
            raw_body = str(body or "").encode("utf-8")
            body = None
        status, response_headers, response_body, error = self._raw_request(
            case.target,
            case.method,
            path,
            headers=headers,
            body=body,
            raw_body=raw_body,
            auth_token=auth_token,
            internal_token=internal_token,
        )
        try:
            assertion = case.assertion
            if self.mode == "public" and case.target in {"graph", "rag"}:
                assertion = Assertion("internal_not_exposed")
            if self.mode == "public" and case.family == "backend-actuator":
                assertion = Assertion("no_secret_file")
            passed = evaluate_assertion(
                assertion, status, response_headers, response_body, mode=self.mode
            )
            outcome = "pass" if passed else "fail"
        except ValueError as exc:
            outcome = "error"
            error = str(exc)
        time.sleep(self.delay)
        return self._result(case, outcome, status, response_headers, response_body, error)

    def run(self, cases: list[AttackCase]) -> list[dict[str, Any]]:
        return [self.run_case(case) for case in cases]

    def _resolve_auth(self, auth_mode: str) -> tuple[str | None, str | None]:
        if auth_mode == "anonymous":
            return None, None
        if auth_mode in self.tokens:
            return self.tokens.get(auth_mode), None
        if auth_mode == "internal":
            return None, self.internal_token
        if auth_mode.startswith("internal_wrong:"):
            return None, auth_mode.split(":", 1)[1]
        if auth_mode.startswith("raw:"):
            return auth_mode.split(":", 1)[1], None
        for prefix in ("jwt_bad_signature", "jwt_none", "jwt_expired", "jwt_subject", "jwt_role"):
            if auth_mode.startswith(prefix + ":"):
                marker = auth_mode.split(":", 1)[1]
                token = _tamper_jwt(self.tokens.get("student_a", marker), prefix, marker)
                return token, None
        return None, None

    def _render(self, value: str) -> str:
        for name, replacement in self.context.items():
            value = value.replace("{{" + name + "}}", str(replacement))
        return value

    def _render_value(self, value: Any) -> Any:
        if isinstance(value, str):
            return self._render(value)
        if isinstance(value, dict):
            return {key: self._render_value(item) for key, item in value.items()}
        if isinstance(value, list):
            return [self._render_value(item) for item in value]
        return value

    def _raw_request(
        self,
        target: str,
        method: str,
        path: str,
        *,
        headers: dict[str, str] | None = None,
        body: Any = None,
        raw_body: bytes | None = None,
        auth_token: str | None = None,
        internal_token: str | None = None,
    ) -> tuple[int | None, dict[str, str], str, str | None]:
        base_url = self.base_urls[target].rstrip("/")
        url = normalize_request_url(base_url + (path if path.startswith("/") else "/" + path))
        request_headers = dict(headers or {})
        if auth_token:
            request_headers["Authorization"] = f"Bearer {auth_token}"
        if internal_token:
            request_headers["X-Internal-Token"] = internal_token
        if raw_body is None and body is not None:
            raw_body = json.dumps(body, ensure_ascii=False).encode("utf-8")
            request_headers.setdefault("Content-Type", "application/json")
        request_headers.setdefault("User-Agent", "Pythontopia-Security-Audit/1.0")
        request = urllib.request.Request(url, data=raw_body, headers=request_headers, method=method)
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                response_body = read_response_body(response)
                return response.status, dict(response.headers.items()), response_body, None
        except urllib.error.HTTPError as exc:
            response_body = read_response_body(exc)
            return exc.code, dict(exc.headers.items()), response_body, None
        except Exception as exc:  # Network/protocol errors are evidence, not suite crashes.
            return None, {}, "", f"{type(exc).__name__}: {exc}"

    @staticmethod
    def _multipart(body: dict[str, Any]) -> tuple[bytes, str]:
        boundary = "----PythontopiaAuditBoundary"
        field = str(body.get("field", "file"))
        filename = str(body.get("filename", "audit.txt"))
        content = body.get("content", "")
        if not isinstance(content, bytes):
            content = str(content).encode("utf-8")
        content_type = str(body.get("content_type", "text/plain"))
        prefix = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
            f"Content-Type: {content_type}\r\n\r\n"
        ).encode("utf-8")
        payload = prefix + content + f"\r\n--{boundary}--\r\n".encode("ascii")
        return payload, f"multipart/form-data; boundary={boundary}"

    @staticmethod
    def _result(
        case: AttackCase,
        outcome: str,
        status: int | None,
        headers: dict[str, str],
        body: str,
        error: str | None,
    ) -> dict[str, Any]:
        return redact_evidence(
            {
                "case_id": case.case_id,
                "domain": case.domain,
                "family": case.family,
                "variant": case.variant,
                "target": case.target,
                "method": case.method,
                "path": case.path,
                "safety": case.safety,
                "assertion": asdict(case.assertion),
                "outcome": outcome,
                "status": status,
                "headers": headers,
                "body": body,
                "error": error,
            }
        )


def _write_jsonl(path: Path, results: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for result in results:
            handle.write(json.dumps(result, ensure_ascii=True, sort_keys=True) + "\n")


def load_existing_results(path: Path) -> tuple[set[str], dict[str, int]]:
    completed: set[str] = set()
    counts: dict[str, int] = {}
    if not path.exists():
        return completed, counts
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        result = json.loads(line)
        completed.add(str(result["case_id"]))
        outcome = str(result["outcome"])
        counts[outcome] = counts.get(outcome, 0) + 1
    return completed, counts


def _run_to_jsonl(
    runner: AuditRunner, path: Path, cases: list[AttackCase], *, resume: bool = False
) -> dict[str, int]:
    path.parent.mkdir(parents=True, exist_ok=True)
    completed, counts = load_existing_results(path) if resume else (set(), {})
    pending = [case for case in cases if case.case_id not in completed]
    mode = "a" if resume else "w"
    with path.open(mode, encoding="utf-8") as handle:
        for pending_index, attack_case in enumerate(pending, start=1):
            result = runner.run_case(attack_case)
            handle.write(json.dumps(result, ensure_ascii=True, sort_keys=True) + "\n")
            handle.flush()
            outcome = str(result["outcome"])
            counts[outcome] = counts.get(outcome, 0) + 1
            progress = len(completed) + pending_index
            if progress % 100 == 0 or pending_index == len(pending):
                print(
                    json.dumps(
                        {"progress": progress, "total": len(cases), "outcomes": counts},
                        sort_keys=True,
                    ),
                    flush=True,
                )
    return counts


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("local", "public-safe", "public-active"), default="local")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--public-base", default="http://47.120.29.62")
    parser.add_argument("--timeout", type=float, default=8.0)
    parser.add_argument("--delay", type=float, default=0.01)
    parser.add_argument("--resume", action="store_true")
    args = parser.parse_args()

    _load_dotenv(Path(__file__).resolve().parents[1] / "deploy" / ".env")
    network_mode = "local" if args.mode == "local" else "public"
    if args.mode == "local":
        base_urls = {
            "frontend": "http://127.0.0.1:5173",
            "backend": "http://127.0.0.1:18080",
            "graph": "http://127.0.0.1:18091",
            "rag": "http://127.0.0.1:18092",
        }
        approved_hosts: set[str] = set()
    else:
        parsed = urllib.parse.urlsplit(args.public_base)
        if not parsed.hostname:
            parser.error("--public-base must include a host")
        approved_hosts = {parsed.hostname}
        # All 1,000 requests still hit the public website. Graph/RAG direct-path cases verify
        # that those internal services are not accidentally routed by the public reverse proxy.
        base_urls = {
            "frontend": args.public_base,
            "backend": args.public_base,
            "graph": args.public_base,
            "rag": args.public_base,
        }

    runner = AuditRunner(
        network_mode,
        base_urls,
        approved_hosts,
        timeout=args.timeout,
        delay=args.delay,
    )
    runner.bootstrap_audit_accounts()
    catalog = select_cases_for_mode(build_attack_catalog(), args.mode)
    counts = _run_to_jsonl(runner, args.output, catalog, resume=args.resume)
    print(json.dumps({"total": len(catalog), "outcomes": counts, "output": str(args.output)}, sort_keys=True))
    return 0 if counts.get("error", 0) == 0 else 2


if __name__ == "__main__":
    raise SystemExit(main())
