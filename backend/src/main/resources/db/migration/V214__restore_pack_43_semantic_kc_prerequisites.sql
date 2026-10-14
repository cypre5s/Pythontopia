-- Purpose: replace V125's synthetic code-order chain with the checked-in pack-43 curriculum prerequisites.
-- Tables affected: course_kc (prerequisite_codes only; no rows are added or removed).
-- Irreversible risk: low; this restores the bundled course-pack source. Reapply the same data from
-- data/course-pack-43/json/course_kc.json if local curriculum prerequisites are intentionally customized.

WITH authoritative_prerequisites (kc_code, prerequisite_codes) AS (
  VALUES
    ('PY43-KC-001', ARRAY[]::text[]),
    ('PY43-KC-002', ARRAY[]::text[]),
    ('PY43-KC-003', ARRAY['PY43-KC-004']::text[]),
    ('PY43-KC-004', ARRAY[]::text[]),
    ('PY43-KC-005', ARRAY['PY43-KC-002']::text[]),
    ('PY43-KC-006', ARRAY[]::text[]),
    ('PY43-KC-007', ARRAY['PY43-KC-006']::text[]),
    ('PY43-KC-008', ARRAY['PY43-KC-003']::text[]),
    ('PY43-KC-009', ARRAY['PY43-KC-015']::text[]),
    ('PY43-KC-010', ARRAY['PY43-KC-008', 'PY43-KC-011']::text[]),
    ('PY43-KC-011', ARRAY['PY43-KC-008']::text[]),
    ('PY43-KC-012', ARRAY['PY43-KC-010']::text[]),
    ('PY43-KC-013', ARRAY['PY43-KC-011', 'PY43-KC-012']::text[]),
    ('PY43-KC-014', ARRAY['PY43-KC-008']::text[]),
    ('PY43-KC-015', ARRAY['PY43-KC-005']::text[]),
    ('PY43-KC-016', ARRAY['PY43-KC-014']::text[]),
    ('PY43-KC-017', ARRAY['PY43-KC-006']::text[]),
    ('PY43-KC-018', ARRAY['PY43-KC-012', 'PY43-KC-023']::text[]),
    ('PY43-KC-019', ARRAY['PY43-KC-010', 'PY43-KC-011']::text[]),
    ('PY43-KC-020', ARRAY['PY43-KC-026']::text[]),
    ('PY43-KC-021', ARRAY['PY43-KC-029']::text[]),
    ('PY43-KC-022', ARRAY['PY43-KC-024']::text[]),
    ('PY43-KC-023', ARRAY['PY43-KC-011', 'PY43-KC-024']::text[]),
    ('PY43-KC-024', ARRAY['PY43-KC-011']::text[]),
    ('PY43-KC-025', ARRAY['PY43-KC-010']::text[]),
    ('PY43-KC-026', ARRAY['PY43-KC-017']::text[]),
    ('PY43-KC-027', ARRAY['PY43-KC-026']::text[]),
    ('PY43-KC-028', ARRAY['PY43-KC-008']::text[]),
    ('PY43-KC-029', ARRAY['PY43-KC-017']::text[]),
    ('PY43-KC-030', ARRAY['PY43-KC-029']::text[]),
    ('PY43-KC-031', ARRAY['PY43-KC-010', 'PY43-KC-013']::text[]),
    ('PY43-KC-032', ARRAY['PY43-KC-033']::text[]),
    ('PY43-KC-033', ARRAY['PY43-KC-010', 'PY43-KC-013']::text[]),
    ('PY43-KC-034', ARRAY['PY43-KC-033']::text[]),
    ('PY43-KC-035', ARRAY['PY43-KC-033']::text[]),
    ('PY43-KC-036', ARRAY['PY43-KC-031', 'PY43-KC-032']::text[]),
    ('PY43-KC-037', ARRAY['PY43-KC-031']::text[]),
    ('PY43-KC-038', ARRAY['PY43-KC-010']::text[]),
    ('PY43-KC-039', ARRAY['PY43-KC-040', 'PY43-KC-028']::text[]),
    ('PY43-KC-040', ARRAY['PY43-KC-045']::text[]),
    ('PY43-KC-041', ARRAY['PY43-KC-042']::text[]),
    ('PY43-KC-042', ARRAY['PY43-KC-043']::text[]),
    ('PY43-KC-043', ARRAY['PY43-KC-011']::text[]),
    ('PY43-KC-044', ARRAY['PY43-KC-045']::text[]),
    ('PY43-KC-045', ARRAY['PY43-KC-011', 'PY43-KC-024']::text[]),
    ('PY43-KC-046', ARRAY['PY43-KC-047']::text[]),
    ('PY43-KC-047', ARRAY['PY43-KC-011']::text[]),
    ('PY43-KC-048', ARRAY['PY43-KC-013']::text[]),
    ('PY43-KC-049', ARRAY['PY43-KC-048', 'PY43-KC-052']::text[]),
    ('PY43-KC-050', ARRAY['PY43-KC-048']::text[]),
    ('PY43-KC-051', ARRAY['PY43-KC-048']::text[]),
    ('PY43-KC-052', ARRAY['PY43-KC-048', 'PY43-KC-040']::text[]),
    ('PY43-KC-053', ARRAY['PY43-KC-052', 'PY43-KC-042']::text[]),
    ('PY43-KC-054', ARRAY['PY43-KC-012']::text[]),
    ('PY43-KC-055', ARRAY['PY43-KC-062']::text[]),
    ('PY43-KC-056', ARRAY['PY43-KC-062']::text[]),
    ('PY43-KC-057', ARRAY['PY43-KC-028']::text[]),
    ('PY43-KC-058', ARRAY['PY43-KC-062']::text[]),
    ('PY43-KC-059', ARRAY['PY43-KC-012']::text[]),
    ('PY43-KC-060', ARRAY['PY43-KC-012']::text[]),
    ('PY43-KC-061', ARRAY['PY43-KC-012', 'PY43-KC-031']::text[]),
    ('PY43-KC-062', ARRAY['PY43-KC-063']::text[]),
    ('PY43-KC-063', ARRAY['PY43-KC-012']::text[]),
    ('PY43-KC-064', ARRAY['PY43-KC-012']::text[]),
    ('PY43-KC-065', ARRAY['PY43-KC-064']::text[])
)
UPDATE course_kc AS kc
SET prerequisite_codes = source.prerequisite_codes
FROM authoritative_prerequisites AS source
WHERE kc.pack_id = 43
  AND kc.kc_code = source.kc_code
  AND kc.prerequisite_codes IS DISTINCT FROM source.prerequisite_codes;
