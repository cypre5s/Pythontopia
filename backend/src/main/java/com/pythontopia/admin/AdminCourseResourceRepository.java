package com.pythontopia.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import com.pythontopia.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Repository;

@Repository
public class AdminCourseResourceRepository {

  private static final String RESOURCES =
      """
      WITH resources AS (
        SELECT 'DOCUMENT'::text AS resource_type, d.id AS resource_id,
               d.original_filename AS title, 'Python语言基础'::text AS category,
               d.status, ''::text AS preview, d.page_count
        FROM course_document d WHERE d.pack_id = 43
        UNION ALL
        SELECT 'CHAPTER', c.id, c.title, '课程章节', 'COURSE_SEED',
               COALESCE(c.summary_markdown, ''), NULL::integer
        FROM course_chapter c WHERE c.pack_id = 43
        UNION ALL
        SELECT 'KNOWLEDGE_POINT', kc.id, kc.kc_name, COALESCE(ch.title, '课程知识点'),
               'COURSE_SEED', COALESCE(kc.summary_markdown, ''), NULL::integer
        FROM course_kc kc LEFT JOIN course_chapter ch ON ch.id = kc.chapter_id
        WHERE kc.pack_id = 43
        UNION ALL
        SELECT 'EXAMPLE', e.id, e.title, COALESCE(kc.kc_name, '课程示例'),
               'COURSE_SEED', COALESCE(e.explanation_markdown, ''), NULL::integer
        FROM course_example e LEFT JOIN course_kc kc
          ON kc.pack_id = e.pack_id AND kc.kc_code = e.kc_code
        WHERE e.pack_id = 43
        UNION ALL
        SELECT 'PROBLEM', p.id, p.title, COALESCE(kc.kc_name, '课程练习'),
               'COURSE_SEED', COALESCE(p.statement_markdown, ''), NULL::integer
        FROM course_problem p LEFT JOIN course_kc kc
          ON kc.pack_id = p.pack_id AND kc.kc_code = p.kc_code
        WHERE p.pack_id = 43
      )
      """;

  private static final String FILTER =
      " WHERE (? = 'ALL' OR r.resource_type = ?::text)"
          + " AND (CAST(? AS text) IS NULL OR position(lower(CAST(? AS text)) in"
          + " lower(concat_ws(' ', r.resource_type, r.resource_id::text, r.title, r.category, r.preview))) > 0)";

  private final JdbcTemplate jdbcTemplate;

  public AdminCourseResourceRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public AdminCourseResourcePageDto page(
      int page, int size, String query, AdminCourseResourceType type) {
    String typeName = type == null ? AdminCourseResourceType.ALL.name() : type.name();
    Long total =
        jdbcTemplate.queryForObject(
            RESOURCES + " SELECT count(*) FROM resources r" + FILTER,
            Long.class,
            typeName,
            typeName,
            query,
            query);
    List<AdminCourseResourceDto> content =
        jdbcTemplate.query(
            RESOURCES
                + " SELECT r.resource_type, r.resource_id, r.title, r.category, r.status, r.preview, r.page_count,"
                + " COALESCE(s.enabled, true) AS enabled FROM resources r"
                + " LEFT JOIN course_resource_state s ON s.pack_id = 43"
                + " AND s.resource_type = r.resource_type AND s.resource_id = r.resource_id"
                + FILTER
                + " ORDER BY r.resource_type, r.category, r.title, r.resource_id LIMIT ? OFFSET ?",
            this::map,
            typeName,
            typeName,
            query,
            query,
            size,
            (long) page * size);
    long count = total == null ? 0 : total;
    int totalPages = count == 0 ? 0 : (int) ((count + size - 1) / size);
    return new AdminCourseResourcePageDto(content, page, size, count, totalPages);
  }

  public void setEnabled(AdminCourseResourceType type, long resourceId, boolean enabled, long adminId) {
    String table =
        switch (type) {
          case DOCUMENT -> "course_document";
          case CHAPTER -> "course_chapter";
          case KNOWLEDGE_POINT -> "course_kc";
          case EXAMPLE -> "course_example";
          case PROBLEM -> "course_problem";
          case ALL -> throw new IllegalArgumentException("ALL is not a resource type");
        };
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + table + " WHERE pack_id = 43 AND id = ?",
            Integer.class,
            resourceId);
    if (count == null || count == 0) {
      throw new ResourceNotFoundException("课程资源不存在");
    }
    jdbcTemplate.update(
        """
        INSERT INTO course_resource_state (pack_id, resource_type, resource_id, enabled, updated_by)
        VALUES (43, ?, ?, ?, ?)
        ON CONFLICT (pack_id, resource_type, resource_id) DO UPDATE
          SET enabled = EXCLUDED.enabled, updated_by = EXCLUDED.updated_by, updated_at = now()
        """,
        type.name(),
        resourceId,
        enabled,
        adminId);
  }

  private AdminCourseResourceDto map(ResultSet rs, int rowNum) throws SQLException {
    return new AdminCourseResourceDto(
        rs.getString("resource_type"),
        rs.getLong("resource_id"),
        rs.getString("title"),
        rs.getString("category"),
        rs.getString("status"),
        rs.getBoolean("enabled"),
        rs.getString("preview"),
        (Integer) rs.getObject("page_count"));
  }
}
