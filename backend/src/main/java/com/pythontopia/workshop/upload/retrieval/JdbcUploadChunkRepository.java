package com.pythontopia.workshop.upload.retrieval;

import com.pythontopia.workshop.upload.retrieval.dto.RetrievalHit;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * {@link UploadChunkRepository} 的 JDBC 实现。pgvector 的 {@code vector} 类型无 JDBC 原生绑定，故以文本字面量
 * {@code [v1,v2,...]} 经 {@code ?::vector} 写入；余弦距离用 {@code <=>}，相似度 = {@code 1 - 距离}
 * （与 {@code JdbcMemoryEmbeddingRepository} 同范式）。
 *
 * <p>检索 SQL 一律 {@code JOIN workshop_upload} 取 {@code filename}（chunk 表按契约不冗余文件名），并以
 * {@code c.user_id = ?} 强制个人语料 scoped。
 */
@Repository
public class JdbcUploadChunkRepository implements UploadChunkRepository {

  private static final RowMapper<RetrievalHit> HIT_MAPPER =
      (rs, rowNum) ->
          new RetrievalHit(
              rs.getLong("upload_id"),
              rs.getString("filename"),
              rs.getInt("chunk_index"),
              rs.getString("text"),
              rs.getDouble("score"));

  private final JdbcTemplate jdbc;

  public JdbcUploadChunkRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void replaceChunks(long uploadId, List<ChunkRow> rows) {
    jdbc.update("DELETE FROM workshop_upload_chunk WHERE upload_id = ?", uploadId);
    if (rows == null || rows.isEmpty()) {
      return;
    }
    jdbc.batchUpdate(
        "INSERT INTO workshop_upload_chunk "
            + "(upload_id, user_id, chunk_index, text, embedding, model) "
            + "VALUES (?, ?, ?, ?, ?::vector, ?)",
        new BatchPreparedStatementSetter() {
          @Override
          public void setValues(PreparedStatement ps, int i) throws SQLException {
            ChunkRow row = rows.get(i);
            ps.setLong(1, row.uploadId());
            ps.setLong(2, row.userId());
            ps.setInt(3, row.chunkIndex());
            ps.setString(4, row.text());
            // null embedding → SQL NULL，?::vector 作用于 NULL 仍是 NULL（向量缺失，检索走字面回退）。
            ps.setString(5, row.embedding() == null ? null : toVectorLiteral(row.embedding()));
            ps.setString(6, row.model());
          }

          @Override
          public int getBatchSize() {
            return rows.size();
          }
        });
  }

  @Override
  public void deleteByUploadId(long uploadId) {
    jdbc.update("DELETE FROM workshop_upload_chunk WHERE upload_id = ?", uploadId);
  }

  @Override
  public int countByUploadId(long uploadId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM workshop_upload_chunk WHERE upload_id = ?",
            Integer.class,
            uploadId);
    return count == null ? 0 : count;
  }

  @Override
  public List<RetrievalHit> searchByCosine(
      long userId, float[] query, List<Long> uploadIds, int limit) {
    if (query == null || query.length == 0 || limit <= 0) {
      return List.of();
    }
    String literal = toVectorLiteral(query);
    StringBuilder sql =
        new StringBuilder(
            "SELECT c.upload_id, u.filename, c.chunk_index, c.text, "
                + "1 - (c.embedding <=> ?::vector) AS score "
                + "FROM workshop_upload_chunk c "
                + "JOIN workshop_upload u ON u.id = c.upload_id "
                + "WHERE c.user_id = ? AND c.embedding IS NOT NULL");
    List<Object> args = new ArrayList<>();
    args.add(literal);
    args.add(userId);
    appendUploadFilter(sql, args, uploadIds);
    sql.append(" ORDER BY c.embedding <=> ?::vector LIMIT ?");
    args.add(literal);
    args.add(limit);
    return jdbc.query(sql.toString(), HIT_MAPPER, args.toArray());
  }

  @Override
  public List<RetrievalHit> searchByLiteral(
      long userId, String query, List<Long> uploadIds, int limit) {
    if (query == null || query.isBlank() || limit <= 0) {
      return List.of();
    }
    StringBuilder sql =
        new StringBuilder(
            "SELECT c.upload_id, u.filename, c.chunk_index, c.text, 0.0 AS score "
                + "FROM workshop_upload_chunk c "
                + "JOIN workshop_upload u ON u.id = c.upload_id "
                + "WHERE c.user_id = ? AND c.text ILIKE ? ESCAPE '\\'");
    List<Object> args = new ArrayList<>();
    args.add(userId);
    args.add("%" + escapeLike(query.strip()) + "%");
    appendUploadFilter(sql, args, uploadIds);
    sql.append(" ORDER BY c.upload_id, c.chunk_index LIMIT ?");
    args.add(limit);
    return jdbc.query(sql.toString(), HIT_MAPPER, args.toArray());
  }

  /** 非空 uploadIds → 追加 {@code AND c.upload_id IN (?,?,...)} 与对应参数。 */
  private static void appendUploadFilter(StringBuilder sql, List<Object> args, List<Long> uploadIds) {
    if (uploadIds == null || uploadIds.isEmpty()) {
      return;
    }
    sql.append(" AND c.upload_id IN (");
    for (int i = 0; i < uploadIds.size(); i++) {
      sql.append(i == 0 ? "?" : ",?");
      args.add(uploadIds.get(i));
    }
    sql.append(')');
  }

  /** 转义 ILIKE 通配符（\ % _），配合 {@code ESCAPE '\'}，避免用户查询里的元字符被当通配。 */
  static String escapeLike(String raw) {
    StringBuilder builder = new StringBuilder(raw.length() + 8);
    for (int i = 0; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      if (ch == '\\' || ch == '%' || ch == '_') {
        builder.append('\\');
      }
      builder.append(ch);
    }
    return builder.toString();
  }

  /** float[] → pgvector 字面量 {@code [v1,v2,...]}。 */
  static String toVectorLiteral(float[] vector) {
    StringBuilder builder = new StringBuilder(vector.length * 8 + 2);
    builder.append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        builder.append(',');
      }
      builder.append(vector[i]);
    }
    builder.append(']');
    return builder.toString();
  }
}
