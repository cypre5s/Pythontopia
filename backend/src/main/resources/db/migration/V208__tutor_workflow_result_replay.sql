-- V208: retain the public Tutor result needed to replay a completed idempotent request.
-- Purpose: let a Web retry recover the same terminal SSE result without another model call.
-- Affected tables: adds nullable result_json to tutor_workflow_run_projection.
-- Irreversible risk: dropping this column removes replay data; stored cards and session state remain.

ALTER TABLE tutor_workflow_run_projection
    ADD COLUMN result_json JSONB;

ALTER TABLE tutor_workflow_run_projection
    ADD CONSTRAINT ck_tutor_workflow_projection_result_object
    CHECK (result_json IS NULL OR jsonb_typeof(result_json) = 'object');
