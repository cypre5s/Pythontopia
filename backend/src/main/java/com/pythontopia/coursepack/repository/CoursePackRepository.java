package com.pythontopia.coursepack.repository;

import com.pythontopia.coursepack.domain.CourseChapter;
import com.pythontopia.coursepack.domain.CourseExample;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.domain.CoursePack;
import com.pythontopia.coursepack.domain.CoursePage;
import com.pythontopia.coursepack.domain.CourseProblem;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CoursePackRepository {

  private final JdbcClient jdbcClient;

  public CoursePackRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public Optional<CoursePack> findPack(Long packId) {
    return jdbcClient
        .sql(
            """
            SELECT id, slug, version, name, primary_language AS primaryLanguage, description, status,
                   document_count AS documentCount, page_count AS pageCount,
                   chapter_count AS chapterCount, kc_count AS kcCount,
                   example_count AS exampleCount, problem_count AS problemCount
            FROM course_pack
            WHERE id = :packId
            """)
        .param("packId", packId)
        .query(CoursePack.class)
        .optional();
  }

  public List<CourseChapter> findChapters(Long packId) {
    return jdbcClient
        .sql(
            """
            SELECT id, pack_id AS packId, order_index AS orderIndex, title,
                   summary_markdown AS summaryMarkdown
            FROM course_chapter c
            WHERE c.pack_id = :packId
              AND NOT EXISTS (SELECT 1 FROM course_resource_state s WHERE s.pack_id = c.pack_id
                AND s.resource_type = 'CHAPTER' AND s.resource_id = c.id AND NOT s.enabled)
            ORDER BY c.order_index
            """)
        .param("packId", packId)
        .query(CourseChapter.class)
        .list();
  }

  public List<CourseKc> findKcs(Long packId, Long chapterId) {
    String chapterFilter = chapterId == null ? "" : " AND c.chapter_id = :chapterId";
    JdbcClient.StatementSpec spec =
        jdbcClient
            .sql(
                """
                SELECT id, pack_id AS packId, kc_code AS kcCode, kc_name AS kcName,
                       chapter_id AS chapterId, chapter_order AS chapterOrder, difficulty,
                       summary_markdown AS summaryMarkdown
                FROM course_kc c
                WHERE c.pack_id = :packId
                  AND NOT EXISTS (SELECT 1 FROM course_resource_state s WHERE s.pack_id = c.pack_id
                    AND s.resource_type = 'KNOWLEDGE_POINT' AND s.resource_id = c.id AND NOT s.enabled)
                """
                    + chapterFilter
                    + " ORDER BY chapter_order, id")
            .param("packId", packId);
    if (chapterId != null) {
      spec = spec.param("chapterId", chapterId);
    }
    return spec.query(CourseKc.class).list();
  }

  public Optional<CourseKc> findKcByCode(Long packId, String kcCode) {
    return jdbcClient
        .sql(
            """
            SELECT id, pack_id AS packId, kc_code AS kcCode, kc_name AS kcName,
                   chapter_id AS chapterId, chapter_order AS chapterOrder, difficulty,
                   summary_markdown AS summaryMarkdown
            FROM course_kc c
            WHERE c.pack_id = :packId AND c.kc_code = :kcCode
              AND NOT EXISTS (SELECT 1 FROM course_resource_state s WHERE s.pack_id = c.pack_id
                AND s.resource_type = 'KNOWLEDGE_POINT' AND s.resource_id = c.id AND NOT s.enabled)
            """)
        .param("packId", packId)
        .param("kcCode", kcCode)
        .query(CourseKc.class)
        .optional();
  }

  /**
   * 读取每个 KC 的先修 KC code 列表（{@code course_kc.prerequisite_codes}）。
   * 供 PathPlanner 做 DAG 拓扑排序。
   */
  public java.util.Map<String, List<String>> findKcPrerequisites(Long packId) {
    java.util.Map<String, List<String>> result = new java.util.HashMap<>();
    jdbcClient
        .sql(
            """
            SELECT kc_code, prerequisite_codes
            FROM course_kc c
            WHERE c.pack_id = :packId
              AND NOT EXISTS (SELECT 1 FROM course_resource_state s WHERE s.pack_id = c.pack_id
                AND s.resource_type = 'KNOWLEDGE_POINT' AND s.resource_id = c.id AND NOT s.enabled)
            """)
        .param("packId", packId)
        .query(
            (rs, rowNum) -> {
              String kcCode = rs.getString("kc_code");
              List<String> prereqs = new ArrayList<>();
              Array array = rs.getArray("prerequisite_codes");
              if (array != null) {
                Object raw = array.getArray();
                if (raw instanceof Object[] values) {
                  for (Object value : values) {
                    if (value != null) {
                      prereqs.add(String.valueOf(value));
                    }
                  }
                }
              }
              result.put(kcCode, prereqs);
              return kcCode;
            })
        .list();
    return result;
  }

  public Optional<CourseProblem> findProblem(Long packId, Long problemId) {
    return jdbcClient
        .sql(
            """
            SELECT id, pack_id AS packId, kc_code AS kcCode, title,
                   statement_markdown AS statementMarkdown,
                   input_description AS inputDescription,
                   output_description AS outputDescription, difficulty,
                   source_label AS sourceLabel
            FROM course_problem p
            WHERE p.pack_id = :packId AND p.id = :problemId
              AND NOT EXISTS (SELECT 1 FROM course_resource_state s WHERE s.pack_id = p.pack_id
                AND s.resource_type = 'PROBLEM' AND s.resource_id = p.id AND NOT s.enabled)
            """)
        .param("packId", packId)
        .param("problemId", problemId)
        .query(CourseProblem.class)
        .optional();
  }

  public List<CourseProblem> findProblems(Long packId, String kcCode) {
    String kcFilter = kcCode == null || kcCode.isBlank() ? "" : " AND p.kc_code = :kcCode";
    JdbcClient.StatementSpec spec =
        jdbcClient
            .sql(
                """
                SELECT id, pack_id AS packId, kc_code AS kcCode, title,
                       statement_markdown AS statementMarkdown,
                       input_description AS inputDescription,
                       output_description AS outputDescription, difficulty,
                       source_label AS sourceLabel
                FROM course_problem p
                WHERE p.pack_id = :packId
                  AND NOT EXISTS (SELECT 1 FROM course_resource_state s WHERE s.pack_id = p.pack_id
                    AND s.resource_type = 'PROBLEM' AND s.resource_id = p.id AND NOT s.enabled)
                """
                    + kcFilter
                    + " ORDER BY p.id")
            .param("packId", packId);
    if (kcCode != null && !kcCode.isBlank()) {
      spec = spec.param("kcCode", kcCode);
    }
    return spec.query(CourseProblem.class).list();
  }

  public List<CourseExample> findExamples(Long packId, String kcCode) {
    String kcFilter = kcCode == null || kcCode.isBlank() ? "" : " AND e.kc_code = :kcCode";
    JdbcClient.StatementSpec spec =
        jdbcClient
            .sql(
                """
                SELECT id, pack_id, kc_code, title, code, explanation_markdown, page_refs
                FROM course_example e
                WHERE e.pack_id = :packId
                  AND NOT EXISTS (SELECT 1 FROM course_resource_state s WHERE s.pack_id = e.pack_id
                    AND s.resource_type = 'EXAMPLE' AND s.resource_id = e.id AND NOT s.enabled)
                """
                    + kcFilter
                    + " ORDER BY e.id")
            .param("packId", packId);
    if (kcCode != null && !kcCode.isBlank()) {
      spec = spec.param("kcCode", kcCode);
    }
    return spec.query(mapExample()).list();
  }

  public Optional<CourseExample> findExample(Long packId, Long exampleId) {
    return jdbcClient
        .sql(
            """
            SELECT id, pack_id, kc_code, title, code, explanation_markdown, page_refs
            FROM course_example e
            WHERE e.pack_id = :packId AND e.id = :exampleId
              AND NOT EXISTS (SELECT 1 FROM course_resource_state s WHERE s.pack_id = e.pack_id
                AND s.resource_type = 'EXAMPLE' AND s.resource_id = e.id AND NOT s.enabled)
            """)
        .param("packId", packId)
        .param("exampleId", exampleId)
        .query(mapExample())
        .optional();
  }

  private RowMapper<CourseExample> mapExample() {
    return (rs, rowNum) ->
        new CourseExample(
            rs.getLong("id"),
            rs.getLong("pack_id"),
            rs.getString("kc_code"),
            rs.getString("title"),
            rs.getString("code"),
            rs.getString("explanation_markdown"),
            readLongArray(rs.getArray("page_refs")));
  }

  private static Long[] readLongArray(Array array) throws SQLException {
    if (array == null) {
      return new Long[0];
    }
    Object raw = array.getArray();
    if (raw instanceof Long[] longs) {
      return longs;
    }
    if (raw instanceof Object[] objects) {
      List<Long> result = new ArrayList<>(objects.length);
      for (Object o : objects) {
        if (o instanceof Number n) {
          result.add(n.longValue());
        }
      }
      return result.toArray(new Long[0]);
    }
    return new Long[0];
  }

  /** 按 page_id 取单条课件页（含 document_id + page_no），供「页面图片」端点定位 PDF 与页码。 */
  public Optional<CoursePage> findPageById(Long pageId) {
    return jdbcClient
        .sql(
            """
            SELECT id, pack_id AS packId, document_id AS documentId,
                   chapter_id AS chapterId, page_no AS pageNo,
                   title, content_markdown AS contentMarkdown, excerpt
            FROM course_page
            WHERE id = :pageId AND NOT EXISTS (
              SELECT 1 FROM course_resource_state s WHERE s.pack_id = course_page.pack_id
                AND s.resource_type = 'DOCUMENT' AND s.resource_id = course_page.document_id AND NOT s.enabled)
            """)
        .param("pageId", pageId)
        .query(CoursePage.class)
        .optional();
  }

  /**
   * 按 pack 内 {@code sort_order, id} 顺位取第 N 份文档 id（1-based）。供主线「章 → 文档」
   * 动态解析（R1）：7 份课件文档按 sort_order 与 7 章一一对应，重灌库 id 漂移不再取错文档。
   */
  public Optional<Long> findDocumentIdByPackOrder(long packId, int position) {
    return jdbcClient
        .sql(
            """
            SELECT id
            FROM course_document
            WHERE pack_id = :packId AND NOT EXISTS (
              SELECT 1 FROM course_resource_state s WHERE s.pack_id = course_document.pack_id
                AND s.resource_type = 'DOCUMENT' AND s.resource_id = course_document.id AND NOT s.enabled)
            ORDER BY sort_order, id
            OFFSET :skip LIMIT 1
            """)
        .param("packId", packId)
        .param("skip", position - 1)
        .query(Long.class)
        .optional();
  }

  /**
   * 某文档的全部课件页 id（按页序 page_no, chunk_index）。供主线「学=整章 PPT 全部页」过线口径：
   * 章 → documentId 由 {@code findDocumentIdByPackOrder} 动态解析（未命中回退 185 + chapter）。
   */
  public List<Long> findPageIdsByDocument(long documentId) {
    return jdbcClient
        .sql(
            """
            SELECT id
            FROM course_page
            WHERE document_id = :documentId AND NOT EXISTS (
              SELECT 1 FROM course_resource_state s WHERE s.pack_id = 43 AND s.resource_type = 'DOCUMENT'
                AND s.resource_id = course_page.document_id AND NOT s.enabled)
            ORDER BY page_no, chunk_index
            """)
        .param("documentId", documentId)
        .query((rs, rowNum) -> rs.getLong("id"))
        .list();
  }

  public List<CoursePage> findPagesByKc(Long packId, String kcCode) {
    return jdbcClient
        .sql(
            """
            SELECT p.id, p.pack_id AS packId, p.document_id AS documentId,
                   p.chapter_id AS chapterId, p.page_no AS pageNo,
                   p.title, p.content_markdown AS contentMarkdown, p.excerpt
            FROM course_page p
            JOIN course_kc kc ON p.id = ANY(kc.page_refs)
            WHERE kc.pack_id = :packId AND kc.kc_code = :kcCode
              AND NOT EXISTS (SELECT 1 FROM course_resource_state ks WHERE ks.pack_id = kc.pack_id
                AND ks.resource_type = 'KNOWLEDGE_POINT' AND ks.resource_id = kc.id AND NOT ks.enabled)
              AND NOT EXISTS (SELECT 1 FROM course_resource_state ds WHERE ds.pack_id = p.pack_id
                AND ds.resource_type = 'DOCUMENT' AND ds.resource_id = p.document_id AND NOT ds.enabled)
            ORDER BY p.document_id, p.page_no, p.chunk_index
            """)
        .param("packId", packId)
        .param("kcCode", kcCode)
        .query(CoursePage.class)
        .list();
  }
}
