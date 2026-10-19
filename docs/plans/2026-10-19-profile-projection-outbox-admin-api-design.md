# Profile Projection Outbox Admin API Design

## Approved scope

Add the smallest admin workflow that can find a quarantined projection, inspect its safe operational metadata, repair and replay it through the existing endpoint, then verify its persisted result. Replay auditing keeps the latest operator and timestamp on the outbox row, alongside the existing cumulative `manual_replay_count`.

## API

- `GET /api/admin/profile-projection-outbox/failed?page=0&size=50` returns failed rows ordered by `failed_at`, then `id`, with the same page envelope used by `AdminUserPageDto`. `page` is non-negative; `size` is 1–100.
- `GET /api/admin/profile-projection-outbox/{outboxId}` returns the current state and operational metadata for one row, including status, user and dimension identifiers, attempt count, safe error kind, lifecycle timestamps, replay count, latest replay operator ID, and latest replay time. A missing ID returns 404.
- The existing `PUT /api/admin/profile-projection-outbox/{outboxId}` remains the repair operation. On acceptance it records the authenticated admin's user ID and current time, increments `manual_replay_count`, and returns `202 ACCEPTED`. Admins use the detail GET to poll until the row is `APPLIED` or `FAILED`.

All endpoints remain ADMIN-only. GET responses do not expose the projection delta or idempotency key. Existing repair validation and conflict behavior remain in place.

## Persistence

Migration V226 adds nullable `last_replayed_by BIGINT` and `last_replayed_at TIMESTAMPTZ` columns and a partial `(failed_at, id)` index for failed-row pagination. The current `manual_replay_count` column is reused. Only the latest replay attribution is retained; there is no per-replay history or payload snapshot.

## Acceptance

- Controller tests cover admin access, student denial, pagination validation, not-found behavior, and forwarding the authenticated admin ID to repair.
- PostgreSQL tests cover oldest-first pagination, detail state, and last-replay metadata.
- A real local admin HTTP flow creates or selects a failed row, lists and inspects it, repairs it, then verifies the row reaches `APPLIED` and its replay count/operator/time are persisted.
- Run the backend test suite for the API and migration changes, then record verification and limits in the work log and changelog.
