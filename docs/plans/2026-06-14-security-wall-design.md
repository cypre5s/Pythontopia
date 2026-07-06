# Pythontopia Security Wall Design

## Objective

Build a repeatable security audit and remediation loop for Pythontopia. The loop must execute exactly 1,000 distinct, traceable attack cases against an isolated local stack, perform a small non-destructive verification set against the public deployment, publish a finding report, and close findings through regression tests and targeted fixes.

## Safety Boundary

- The complete active suite targets `127.0.0.1` only and uses disposable audit accounts and data.
- Public ECS checks are low-frequency and non-destructive: headers, CORS, anonymous authorization, error disclosure, method handling, and public resource policy.
- No denial-of-service, destructive database payload, persistence, credential theft, cloud metadata access, lateral movement, or extraction of real user data is attempted on ECS.
- Potentially dangerous classes such as sandbox escape and SSRF are proven with local canaries or static tests, never real external targets.

## Approaches Considered

### 1. Scanner-only

Run an off-the-shelf web scanner and export its report. This is quick but poorly covers Pythontopia's JWT roles, object ownership, graph/RAG internal token, learning evidence, uploads, and Python judge boundaries.

### 2. Handwritten endpoint tests only

Write focused integration tests for known controls. This produces strong regression tests but does not satisfy broad attack-surface discovery or the requested 1,000-case inventory.

### 3. Hybrid catalog and regression loop

Use a deterministic attack catalog plus project-native tests. The catalog provides breadth and auditable case IDs; confirmed findings are converted into narrow JUnit, Vitest, or pytest regressions before fixes. This is the selected approach.

## Architecture

The audit has four layers:

1. `security/catalog`: a deterministic generator produces exactly 1,000 unique cases across authentication, authorization, injection, browser security, uploads, sandboxing, service-to-service authentication, protocol handling, and deployment configuration.
2. `security/runner`: a rate-limited runner executes cases against an allowlisted target. It records request metadata, status, response fingerprint, assertion result, and sanitized evidence as JSONL.
3. `security/report`: a report builder groups failed assertions into findings, assigns severity and CWE/OWASP references, and tracks `open`, `fixed`, `accepted`, or `not_reproducible` status.
4. Native regression tests: every confirmed Critical or High finding, and every code-fixed Medium finding, receives a failing test before production code changes.

## Attack Matrix

The 1,000 cases are allocated across ten domains, with 100 unique cases each:

- Authentication and JWT integrity
- Function-level and object-level authorization
- SQL, command, template, expression, and header injection
- XSS, Markdown, URL, and browser navigation handling
- CORS, CSRF, clickjacking, CSP, and security headers
- Upload, parser, path traversal, archive, and media handling
- Python judge resource limits, network/filesystem boundaries, and static checks
- Graph/RAG internal authentication, prompt boundary, SSRF, and error handling
- HTTP method, content type, parser ambiguity, cache, and proxy behavior
- Secrets, production configuration, dependency, image, and release integrity

Cases are distinct by control, endpoint, role, payload family, encoding, transport shape, or expected invariant. Merely repeating the same payload with random text does not count.

## Data Flow

The runner creates two student accounts and one teacher account using an audit-only prefix, obtains tokens, discovers the local OpenAPI surface, and executes anonymous, student A, student B, teacher, expired-token, and tampered-token cases. IDs created by student A are used as ownership canaries when student B is tested for BOLA/IDOR.

Responses are capped before storage. Authorization headers, cookies, tokens, passwords, and internal service tokens are redacted. The report links each finding to case IDs and a native regression test when fixed.

## Failure Handling

- A target outside the explicit local or approved public allowlist aborts the run.
- Connection failures are recorded as infrastructure errors, not security passes.
- `5xx`, stack traces, reflected canaries, unauthorized `2xx`, permissive CORS, and missing mandatory headers are findings according to case-specific assertions.
- Rate-limit tests use bounded bursts locally. Public checks never use burst or concurrency attacks.

## Acceptance

- The catalog contains exactly 1,000 unique IDs and 1,000 unique case fingerprints.
- All local cases execute and produce machine-readable evidence.
- Public verification uses only the approved non-destructive subset.
- A Markdown report lists findings by severity with evidence, affected components, remediation, status, and residual risk.
- Confirmed findings are fixed in severity order with red-green regression evidence.
- Final verification reruns the 1,000 local cases plus relevant project test suites and distinguishes fixed, accepted, unverified, and remaining risks.
