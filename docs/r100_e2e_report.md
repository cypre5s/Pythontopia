# R-100 Judge E2E Docker Compose Report

**Date**: 2026-05-22  
**Scope**: `deploy/docker-compose.yml` 6-service stack + judge/Bug Mine mainline API.

## Compose Status

`docker compose ps` after validation:

| Service | Image | Status |
| --- | --- | --- |
| postgres | `pgvector/pgvector:pg16` | healthy |
| redis | `redis:7-alpine` | healthy |
| backend | `pythontopia-backend:dev` | healthy |
| pythontopia-graph | `pythontopia-graph:dev` | healthy |
| pythontopia-rag | `pythontopia-rag:dev` | healthy |
| frontend | `pythontopia-frontend:dev` | healthy |

## Build Note

Direct `docker compose up -d --build` initially failed because Docker daemon could not fetch Docker Hub base image metadata through the configured mirror:

```text
docker.m.daocloud.io ... TLS handshake timeout
```

To keep validation moving without changing repository Dockerfiles, local `pythontopia-*:dev` images were built from already-available local image layers and host build artifacts, then compose was started with `--no-build`. Python dependency installation for the graph/RAG images used host networking and the requested proxy:

```text
HTTP_PROXY=http://127.0.0.1:7892
HTTPS_PROXY=http://127.0.0.1:7892
```

Local localhost API checks used `NO_PROXY='*'` to avoid proxying `127.0.0.1`.

## Smoke

Command:

```bash
env HTTP_PROXY= HTTPS_PROXY= http_proxy= https_proxy= NO_PROXY='*' no_proxy='*' \
  bash deploy/scripts/demo_smoke.sh
```

Result:

```text
backend /healthz ok
graph /healthz ok
rag /healthz ok
frontend /healthz ok
course pack 43 readable
```

`deploy/scripts/demo_smoke.sh` was corrected from stale `GET /api/course/packs/43` to the current backend route `GET /api/course/pack`.

## Mainline API E2E

The E2E exercised the full teacher/student/judge/Bug Mine path:

| Step | Endpoint | Result |
| --- | --- | --- |
| Register teacher | `POST /api/auth/register` | 201 |
| Register student | `POST /api/auth/register` | 201 |
| Login teacher/student | `POST /api/auth/login` | 200 |
| Create classroom | `POST /api/classrooms` | 201 |
| Join classroom | `POST /api/classrooms/join` | 200 |
| Publish class quest | `POST /api/teacher/classrooms/{id}/quests` | 201 |
| Student class quests | `GET /api/classroom/quests/mine` | 200 |
| Student today quests | `GET /api/quests/today` | 200 |
| Start quest | `POST /api/quests/{id}/start` | 200 |
| Failed submission | `POST /api/submissions` | 200, `FAILED` |
| Submission detail | `GET /api/submissions/{id}` | 200, `WA` |
| Open Bug Mine | `POST /api/bug-mine/sessions` | 200, 6 cards |
| Fetch Bug Mine | `GET /api/bug-mine/sessions/{id}` | 200 |
| Retry submit | `POST /api/bug-mine/sessions/{id}/retry-submit` | 200, `PASSED` |

Run artifact:

```json
{
  "teacher": "r100t1779428166",
  "student": "r100s1779428166",
  "classroomId": 1,
  "classroomQuestId": 1,
  "questId": 1,
  "failedSubmissionId": 1,
  "bugMineSessionId": 1,
  "retrySubmissionId": 2,
  "verdict": "WA"
}
```

Backend log confirms quest auto-completion after the Bug Mine retry submission:

```text
Quest 1 auto-completed via submission 2
```

Graph log confirms Bug Mine called the real debug endpoint:

```text
POST /agents/debug/analyze HTTP/1.1 200 OK
```

## Result

Task 4 passed with all 6 compose services healthy and the judge/Bug Mine mainline verified end to end. The only repository code fix required was the stale smoke endpoint path.
