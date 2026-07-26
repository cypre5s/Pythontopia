package com.pythontopia.download;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 客户端安装包下载次数统计（JdbcTemplate，与本仓库数据访问风格一致）。
 *
 * <p>{@code app_download_stat(stat_key PK, hit_count)} 单表原子自增。计数仅用于运营展示，
 * 不与业务/判题数据耦合；端点随 {@code /api/downloads/**} 匿名放行（guide 页点击即记一次）。
 *
 * <p><b>只更新既有行，绝不建行</b>：自增走 {@code UPDATE ... WHERE stat_key = ?}，命中 0 行即
 * {@link #increment} 返回 empty，控制器回 404。历史实现是 {@code INSERT ... ON CONFLICT DO UPDATE}，
 * 任何人对匿名端点用任意 key 调一次就能凭空建行——控制器上的 {@code [a-z0-9_]{1,64}} 正则只约束
 * 字符集、不约束 key 集合，挡不住这种垃圾行注入。合法 key 由迁移 seed（V174 的 {@code android_apk}），
 * 新增客户端请走迁移加行，而不是让端点自己长表。
 */
@Service
public class DownloadStatService {

  private final JdbcTemplate jdbcTemplate;

  public DownloadStatService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** 读当前计数；无记录返回 empty（控制器据此回 404）。 */
  public Optional<Long> count(String key) {
    List<Long> rows =
        jdbcTemplate.query(
            "SELECT hit_count FROM app_download_stat WHERE stat_key = ?",
            (rs, i) -> rs.getLong(1),
            key);
    return rows.stream().findFirst();
  }

  /** 原子自增并返回新计数；key 未由迁移 seed（更新 0 行）时返回 empty，不建行。 */
  public Optional<Long> increment(String key) {
    int updated =
        jdbcTemplate.update(
            "UPDATE app_download_stat"
                + " SET hit_count = hit_count + 1, updated_at = now()"
                + " WHERE stat_key = ?",
            key);
    if (updated == 0) {
      return Optional.empty();
    }
    return count(key);
  }
}
