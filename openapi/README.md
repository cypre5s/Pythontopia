# Pythontopia Client-Facing OpenAPI

This folder contains a **curated, hand-authored** OpenAPI 3.0.3 description of the
**client-facing** REST surface of the Pythontopia backend (Spring Boot, Java 21).

- Spec file: [`pythontopia-api.json`](./pythontopia-api.json)
- OpenAPI version: `3.0.3`
- Intended consumers: native clients — **Windows (.NET)**, **Android**, **WeChat mini-program** — for code generation and reference.

> This is **not** the exhaustive backend spec. It deliberately covers only the endpoints
> that end-user clients call (auth, world/game, learner, quests, course, practice, classroom,
> and the streaming agent endpoints), plus public media/health. Teacher dashboards, RAG admin,
> intent routing, bug-mine internals, TTS/image generation, and similar server-internal
> surfaces are intentionally omitted. For the complete, always-in-sync contract, regenerate
> from live springdoc (see [Regenerating the FULL spec](#regenerating-the-full-spec)).

## Contract summary

| Aspect | Value |
| --- | --- |
| Base URL (desktop / WeChat devtools) | `http://localhost:18080` |
| Base URL (Android emulator → host) | `http://10.0.2.2:18080` |
| REST prefix | `/api` (no servlet context path); `/healthz` lives at the root |
| Auth | Bearer JWT (HS256), 12h TTL — `Authorization: Bearer <token>` |
| Token refresh | `POST /api/auth/refresh` re-issues a token from a **still-valid** JWT (no separate refresh token). On `401`, re-login via `POST /api/auth/login`. |
| Public endpoints (no JWT) | `/api/auth/**`, `/api/course/**`, `GET /api/media/assets/{id}`, `GET /healthz` |
| Error envelope | `{ code, message, details[], traceId, timestamp }` (`ApiError`) |
| Streaming | SSE (`text/event-stream`) with named events: `plan`, `trace`, `token`, `result`, `error` |

In the spec, `bearerAuth` is applied globally; public operations override it with an empty
`security: []`.

### Counts (this curated spec)

- **63** path items / **65** operations (**17** public, **48** secured)
- **86** component schemas, **4** reusable error responses (`BadRequest`, `Unauthorized`, `Forbidden`, `NotFound`)

## Validate that it parses

```bash
node -e "JSON.parse(require('fs').readFileSync('openapi/pythontopia-api.json','utf8')); console.log('ok')"
```

Optional, fuller OpenAPI validation (requires network to fetch the tool once):

```bash
# Redocly CLI
npx --yes @redocly/cli@latest lint openapi/pythontopia-api.json

# or swagger-cli
npx --yes @apidevtools/swagger-cli@latest validate openapi/pythontopia-api.json
```

## Regenerating the FULL spec

The backend serves a complete, auto-generated spec via **springdoc** while it is running.
That endpoint is permitted anonymously (see `SecurityConfig`).

```bash
# Backend must be running on :18080
curl -s http://localhost:18080/v3/api-docs -o openapi/pythontopia-api.full.json

# (optional) pretty-print
node -e "const fs=require('fs');const j=JSON.parse(fs.readFileSync('openapi/pythontopia-api.full.json','utf8'));fs.writeFileSync('openapi/pythontopia-api.full.json',JSON.stringify(j,null,2));console.log('formatted')"
```

Notes:
- The full springdoc document covers **every** controller (including teacher/internal ones)
  and emits springdoc's auto-derived schema names; it is the source of truth when the two
  disagree. This curated file trades completeness for a smaller, client-focused surface with
  hand-written descriptions, examples, validation constraints, and SSE documentation.
- springdoc emits OpenAPI **3.1** (or 3.0 depending on the configured version). If your codegen
  toolchain needs strict 3.0, prefer this curated file or down-convert the full spec.

## Code generation examples

Using [`openapi-generator`](https://openapi-generator.tech/) (via npx; a JDK is required at
runtime). Replace `INPUT` as needed; here we use the curated spec.

```bash
INPUT=openapi/pythontopia-api.json
```

### C# (.NET — Windows client)

```bash
npx --yes @openapitools/openapi-generator-cli generate \
  -i "$INPUT" \
  -g csharp \
  -o clients/csharp \
  --additional-properties=packageName=Pythontopia.ApiClient,targetFramework=net8.0,library=httpclient,nullableReferenceTypes=true
```

### Kotlin (Android client)

```bash
npx --yes @openapitools/openapi-generator-cli generate \
  -i "$INPUT" \
  -g kotlin \
  -o clients/kotlin \
  --additional-properties=library=jvm-retrofit2,serializationLibrary=kotlinx_serialization,packageName=com.pythontopia.apiclient,useCoroutines=true
```

> The Android app per `AGENTS.md` uses Retrofit 2 + `kotlinx-serialization` + coroutines, which
> matches the options above. DTOs may also be generated standalone (`-g kotlin` with
> `--global-property=models`) if you prefer to keep the existing Retrofit interfaces by hand.

### TypeScript fetch (WeChat mini-program / web reference)

```bash
npx --yes @openapitools/openapi-generator-cli generate \
  -i "$INPUT" \
  -g typescript-fetch \
  -o clients/typescript-fetch \
  --additional-properties=supportsES6=true,typescriptThreePlus=true,withInterfaces=true
```

> WeChat mini-programs cannot use the DOM `fetch`/`XMLHttpRequest` directly (they use
> `wx.request`). The `typescript-fetch` output is still useful for the **generated models and
> param types**; wire the actual transport to `wx.request` (or a fetch polyfill) in a thin
> adapter.

## Streaming (SSE) endpoints — client note

These return `text/event-stream`, not JSON, and must be consumed as a stream:

- `POST /api/tutor/ask`
- `POST /api/orchestrator/turn/stream`
- `POST /api/workshop/resources/stream`
- `POST /api/learner/profile/interview/{sessionId}/turn/stream`

Each SSE message carries an `event:` name (`plan` | `trace` | `token` | `result` | `error`)
and a `data:` line with a JSON payload. openapi-generator will model the response as a raw
string/stream; implement an SSE reader on the client (e.g. `EventSource`-style parsing, or
manual line buffering over the response stream since these are `POST` requests with a body).

## Maintenance

When backend DTOs or controllers change, update `pythontopia-api.json` by hand (this file is
not auto-generated) and re-run the validation command above, **or** switch to regenerating the
full spec from springdoc and treat this curated file as a documented subset.
