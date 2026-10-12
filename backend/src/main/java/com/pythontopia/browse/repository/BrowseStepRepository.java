package com.pythontopia.browse.repository;

import com.pythontopia.browse.entity.BrowseStepEntity;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code browse_step} 的 JdbcTemplate 仓储。步骤随会话整体写入（批量 insert），读取按 {@code run_id}
 * 序号升序。本表无 user_id，归属由父 {@code browse_run} 把关（service 先以 user 校验 run 再取步骤）。
 */
@Repository
public class BrowseStepRepository {

  private final JdbcTemplate jdbcTemplate;

  public BrowseStepRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 批量写入某会话的全部步骤（按 idx 落库）；空列表为 no-op。 */
  public void insertAll(long runId, List<BrowseStepEntity> steps) {
    if (steps == null || steps.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(
        """
        INSERT INTO browse_step (run_id, idx, action, url, summary, title, snippet)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            BrowseStepEntity step = steps.get(i);
            ps.setLong(1, runId);
            ps.setInt(2, step.idx());
            ps.setString(3, step.action());
            ps.setString(4, step.url());
            ps.setString(5, step.summary());
            ps.setString(6, step.title());
            ps.setString(7, step.snippet());
          }

          @Override
          public int getBatchSize() {
            return steps.size();
          }
        });
  }

  /** 取某会话的全部步骤，按 idx 升序（呈现顺序）。 */
  public List<BrowseStepEntity> findByRunId(long runId) {
    return jdbcTemplate.query(
        """
        SELECT id, run_id, idx, action, url, summary, title, snippet
        FROM browse_step
        WHERE run_id = ?
        ORDER BY idx ASC
        """,
        this::map,
        runId);
  }

  private BrowseStepEntity map(ResultSet rs, int rowNum) throws SQLException {
    return new BrowseStepEntity(
        rs.getLong("id"),
        rs.getLong("run_id"),
        rs.getInt("idx"),
        rs.getString("action"),
        rs.getString("url"),
        rs.getString("summary"),
        rs.getString("title"),
        rs.getString("snippet"));
  }
}
