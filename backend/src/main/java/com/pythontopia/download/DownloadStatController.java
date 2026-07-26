package com.pythontopia.download;

import com.pythontopia.common.error.BusinessException;
import com.pythontopia.download.dto.DownloadStatDto;
import jakarta.validation.constraints.Pattern;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端安装包下载统计端点（匿名放行，见 SecurityConfig {@code /api/downloads/**}）。
 *
 * <ul>
 *   <li>{@code GET /api/downloads/{key}}：读累计次数（页面展示）。
 *   <li>{@code POST /api/downloads/{key}/hit}：点击下载即 +1（前端在跳转下载前调用）。
 * </ul>
 *
 * <p>安装包二进制本身由主机 Nginx {@code location /downloads/}（{@code /opt/pythontopia/downloads/}）
 * 直出，不经后端，避免大文件占用应用线程。
 *
 * <p><b>key 集合由迁移 seed 决定</b>：两端都只认 {@code app_download_stat} 里已存在的行，未知 key
 * 一律 {@code 404 DOWNLOAD_STAT_NOT_FOUND}。下面的 {@code [a-z0-9_]{1,64}} 正则只是字符集约束
 * （挡住路径穿越/超长 key），<em>不</em>等于 key 白名单——真正防"任意建行"的是 service 层改用
 * {@code UPDATE ... WHERE stat_key = ?}。新增客户端请在迁移里加 seed 行。
 */
@Validated
@RestController
@RequestMapping("/api/downloads")
public class DownloadStatController {

  private static final String KEY_PATTERN = "[a-z0-9_]{1,64}";

  private final DownloadStatService service;

  public DownloadStatController(DownloadStatService service) {
    this.service = service;
  }

  @GetMapping("/{key}")
  public DownloadStatDto stat(@PathVariable @Pattern(regexp = KEY_PATTERN) String key) {
    return service.count(key).map(count -> new DownloadStatDto(key, count)).orElseThrow(notFound());
  }

  @PostMapping("/{key}/hit")
  public DownloadStatDto hit(@PathVariable @Pattern(regexp = KEY_PATTERN) String key) {
    return service
        .increment(key)
        .map(count -> new DownloadStatDto(key, count))
        .orElseThrow(notFound());
  }

  private static Supplier<BusinessException> notFound() {
    return () ->
        new BusinessException(HttpStatus.NOT_FOUND, "DOWNLOAD_STAT_NOT_FOUND", "未知的下载统计项");
  }
}
