-- Purpose: derive each mainline stage's courseware pages from its assigned KC page_refs.
-- Tables affected: mainline_stage.courseware_page_ids; mainline_stage_progress.completed_at.
-- Irreversible risk: completion timestamps lacking the newly required KC-page evidence are cleared;
-- video, page-read, and answer history remain intact, but those stages must be completed again.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM mainline_stage stage
    CROSS JOIN LATERAL unnest(stage.kc_codes) AS stage_kc(kc_code)
    LEFT JOIN course_kc kc
      ON kc.pack_id = 43
     AND kc.kc_code = stage_kc.kc_code
    WHERE kc.id IS NULL
       OR NOT EXISTS (
         SELECT 1
         FROM unnest(kc.page_refs) AS page_ref(page_id)
         JOIN course_page page
           ON page.id = page_ref.page_id
          AND page.pack_id = 43
         JOIN course_document document
           ON document.id = page.document_id
          AND document.pack_id = 43
          AND document.sort_order = stage.chapter
       )
  ) THEN
    RAISE EXCEPTION 'mainline KC courseware mapping is incomplete';
  END IF;
END $$;

UPDATE mainline_stage stage
SET courseware_page_ids = (
  SELECT COALESCE(
    array_agg(page_id ORDER BY page_no, chunk_index, page_id),
    ARRAY[]::BIGINT[]
  ) AS page_ids
  FROM (
    SELECT DISTINCT page.id AS page_id, page.page_no, page.chunk_index
    FROM unnest(stage.kc_codes) AS stage_kc(kc_code)
    JOIN course_kc kc
      ON kc.pack_id = 43
     AND kc.kc_code = stage_kc.kc_code
    CROSS JOIN LATERAL unnest(kc.page_refs) AS page_ref(page_id)
    JOIN course_page page
      ON page.id = page_ref.page_id
     AND page.pack_id = 43
    JOIN course_document document
      ON document.id = page.document_id
     AND document.pack_id = 43
     AND document.sort_order = stage.chapter
  ) pages
)
WHERE cardinality(stage.kc_codes) > 0;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM mainline_stage
    WHERE cardinality(courseware_page_ids) = 0
  ) THEN
    RAISE EXCEPTION 'mainline stage has no mapped KC courseware pages';
  END IF;
END $$;

UPDATE mainline_stage_progress progress
SET completed_at = NULL
FROM mainline_stage stage
WHERE progress.stage_id = stage.id
  AND progress.completed_at IS NOT NULL
  AND NOT (progress.pages_read @> stage.courseware_page_ids);
