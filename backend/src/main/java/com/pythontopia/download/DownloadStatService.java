package com.pythontopia.download;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 客户端安装包下载次数统计（JdbcTemplate，与本仓库数据访问风格一致）。
 *
 * <p>{@code app_download_stat(stat_key PK, hit_count)} 单表原子自增。计数仅用于运营展示，
 * 不与业务/判题数据耦合；端点随 {@code /api/downloads/**} 匿名放行（guide 页点击即记一次）。
 */
@Service
public class DownloadStatService {

  private final JdbcTemplate jdbcTemplate;

  public DownloadStatService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 读当前计数；无记录视为 0。 */
  public long count(String key) {
    List<Long> rows =
        jdbcTemplate.query(
            "SELECT hit_count FROM app_download_stat WHERE stat_key = ?",
            (rs, i) -> rs.getLong(1),
            key);
    return rows.stream().findFirst().orElse(0L);
  }

  /** 原子自增并返回新计数（UPSERT：首次点击自动建行）。 */
  public long increment(String key) {
    jdbcTemplate.update(
        "INSERT INTO app_download_stat (stat_key, hit_count, updated_at)"
            + " VALUES (?, 1, now())"
            + " ON CONFLICT (stat_key) DO UPDATE SET"
            + " hit_count = app_download_stat.hit_count + 1, updated_at = now()",
        key);
    return count(key);
  }
}
