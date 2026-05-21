package com.pythontopia.coursepack.repository;

import com.pythontopia.coursepack.domain.CourseChapter;
import com.pythontopia.coursepack.domain.CourseKc;
import com.pythontopia.coursepack.domain.CoursePack;
import com.pythontopia.coursepack.domain.CoursePage;
import com.pythontopia.coursepack.domain.CourseProblem;
import java.util.List;
import java.util.Optional;
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
            FROM course_chapter
            WHERE pack_id = :packId
            ORDER BY order_index
            """)
        .param("packId", packId)
        .query(CourseChapter.class)
        .list();
  }

  public List<CourseKc> findKcs(Long packId, Long chapterId) {
    String chapterFilter = chapterId == null ? "" : " AND chapter_id = :chapterId";
    JdbcClient.StatementSpec spec =
        jdbcClient
            .sql(
                """
                SELECT id, pack_id AS packId, kc_code AS kcCode, kc_name AS kcName,
                       chapter_id AS chapterId, chapter_order AS chapterOrder, difficulty,
                       summary_markdown AS summaryMarkdown
                FROM course_kc
                WHERE pack_id = :packId
                """
                    + chapterFilter
                    + " ORDER BY chapter_order, id")
            .param("packId", packId);
    if (chapterId != null) {
      spec = spec.param("chapterId", chapterId);
    }
    return spec.query(CourseKc.class).list();
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
            FROM course_problem
            WHERE pack_id = :packId AND id = :problemId
            """)
        .param("packId", packId)
        .param("problemId", problemId)
        .query(CourseProblem.class)
        .optional();
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
            ORDER BY p.document_id, p.page_no, p.chunk_index
            """)
        .param("packId", packId)
        .param("kcCode", kcCode)
        .query(CoursePage.class)
        .list();
  }
}
