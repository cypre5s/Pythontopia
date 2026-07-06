-- Purpose: document the local pythontopia database initialization entrypoint.
-- Docker's official Postgres entrypoint creates the pythontopia database from POSTGRES_DB
-- before running this file. Flyway migrations are applied by the backend on startup.
SELECT 'pythontopia database initialized by POSTGRES_DB' AS message;
