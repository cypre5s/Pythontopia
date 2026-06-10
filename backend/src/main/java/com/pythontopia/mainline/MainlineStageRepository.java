package com.pythontopia.mainline;

import com.pythontopia.mainline.domain.MainlineStage;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** mainline_stage 静态定义只读仓库（seed 数据，全局共享）。 */
@Repository
public class MainlineStageRepository {

  private static final String SELECT_COLUMNS =
      "id, chapter, stage_index, title, kc_codes, video_keyword, courseware_page_ids,"
          + " example_problem_ids, required_video_sec, required_pages, required_example_count";

  private final JdbcTemplate jdbcTemplate;

  public MainlineStageRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 全部 stage，按 (chapter, stage_index) 升序——overview 单查询的静态半边。 */
  public List<MainlineStage> findAll() {
    return jdbcTemplate.query(
        "SELECT " + SELECT_COLUMNS + " FROM mainline_stage ORDER BY chapter, stage_index",
        this::map);
  }

  public List<MainlineStage> findByChapter(int chapter) {
    return jdbcTemplate.query(
        "SELECT "
            + SELECT_COLUMNS
            + " FROM mainline_stage WHERE chapter = ? ORDER BY stage_index",
        this::map,
        chapter);
  }

  public Optional<MainlineStage> findById(long stageId) {
    return jdbcTemplate
        .query("SELECT " + SELECT_COLUMNS + " FROM mainline_stage WHERE id = ?", this::map, stageId)
        .stream()
        .findFirst();
  }

  private MainlineStage map(ResultSet rs, int rowNum) throws SQLException {
    return new MainlineStage(
        rs.getLong("id"),
        rs.getInt("chapter"),
        rs.getInt("stage_index"),
        rs.getString("title"),
        readStringArray(rs.getArray("kc_codes")),
        rs.getString("video_keyword"),
        readLongArray(rs.getArray("courseware_page_ids")),
        readLongArray(rs.getArray("example_problem_ids")),
        rs.getInt("required_video_sec"),
        rs.getInt("required_pages"),
        rs.getInt("required_example_count"));
  }

  static List<String> readStringArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    List<String> out = new ArrayList<>();
    if (raw instanceof Object[] values) {
      for (Object value : values) {
        if (value != null) {
          out.add(value.toString());
        }
      }
    }
    return out;
  }

  static List<Long> readLongArray(Array array) throws SQLException {
    if (array == null) {
      return List.of();
    }
    Object raw = array.getArray();
    List<Long> out = new ArrayList<>();
    if (raw instanceof Object[] values) {
      for (Object value : values) {
        if (value instanceof Number number) {
          out.add(number.longValue());
        }
      }
    }
    return out;
  }
}
