package com.pythontopia.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.download.dto.DownloadStatDto;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * 下载计数端点的匿名可写面收口回归。
 *
 * <p>{@code POST /api/downloads/{key}/hit} 随 {@code /api/downloads/**} 匿名放行，历史实现是
 * {@code INSERT ... ON CONFLICT DO UPDATE}——任何人用任意合法字符集的 key 调一次就能建行
 * （控制器的 {@code [a-z0-9_]{1,64}} 只限字符集不限 key 集合）。修复后自增只允许 {@code UPDATE}
 * 迁移已 seed 的行，更新 0 行即 404。
 */
class DownloadStatServiceTest {

  private static final String SEEDED_KEY = "android_apk";
  private static final String UNKNOWN_KEY = "attacker_injected_key";

  private JdbcTemplate jdbcTemplate;
  private DownloadStatService service;
  private DownloadStatController controller;

  @BeforeEach
  void setUp() {
    jdbcTemplate = mock(JdbcTemplate.class);
    service = new DownloadStatService(jdbcTemplate);
    controller = new DownloadStatController(service);
  }

  @Test
  @DisplayName("自增只发 UPDATE，绝不 INSERT/UPSERT 建行")
  void incrementIssuesUpdateOnly() {
    when(jdbcTemplate.update(anyString(), eq(SEEDED_KEY))).thenReturn(1);
    stubCount(SEEDED_KEY, 8L);

    assertThat(service.increment(SEEDED_KEY)).contains(8L);

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbcTemplate).update(sql.capture(), eq(SEEDED_KEY));
    assertThat(sql.getValue()).startsWith("UPDATE app_download_stat");
    assertThat(sql.getValue()).doesNotContain("INSERT").doesNotContain("ON CONFLICT");
  }

  @Test
  @DisplayName("未 seed 的 key 自增命中 0 行 → empty，控制器回 404 且不查计数")
  void unknownKeyIsRejectedWithoutCreatingRow() {
    when(jdbcTemplate.update(anyString(), eq(UNKNOWN_KEY))).thenReturn(0);

    assertThat(service.increment(UNKNOWN_KEY)).isEmpty();
    verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), eq(UNKNOWN_KEY));

    assertThatThrownBy(() -> controller.hit(UNKNOWN_KEY))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex -> {
              BusinessException business = (BusinessException) ex;
              assertThat(business.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(business.code()).isEqualTo("DOWNLOAD_STAT_NOT_FOUND");
            });
  }

  @Test
  @DisplayName("已 seed 的 key 走通读取与自增")
  void seededKeyReadsAndIncrements() {
    when(jdbcTemplate.update(anyString(), eq(SEEDED_KEY))).thenReturn(1);
    stubCount(SEEDED_KEY, 42L);

    DownloadStatDto read = controller.stat(SEEDED_KEY);
    assertThat(read.key()).isEqualTo(SEEDED_KEY);
    assertThat(read.count()).isEqualTo(42L);

    assertThat(controller.hit(SEEDED_KEY).count()).isEqualTo(42L);
  }

  @Test
  @DisplayName("未知 key 读取也回 404，不再谎报 0 次")
  void unknownKeyReadIsNotFound() {
    stubMissing(UNKNOWN_KEY);

    assertThatThrownBy(() -> controller.stat(UNKNOWN_KEY)).isInstanceOf(BusinessException.class);
  }

  @SuppressWarnings("unchecked")
  private void stubCount(String key, long value) {
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(key)))
        .thenReturn((List) List.of(value));
  }

  @SuppressWarnings("unchecked")
  private void stubMissing(String key) {
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(key))).thenReturn(List.of());
  }
}
