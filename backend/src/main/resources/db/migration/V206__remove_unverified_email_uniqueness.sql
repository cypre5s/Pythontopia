-- Purpose: stop exposing whether an optional, unverified email is registered through a 409 response.
-- Tables affected: users.
-- Irreversible risk: no data is changed; restoring uniqueness can fail after users reuse an email.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;
