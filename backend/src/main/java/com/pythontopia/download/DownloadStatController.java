package com.pythontopia.download;

import com.pythontopia.download.dto.DownloadStatDto;
import jakarta.validation.constraints.Pattern;
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
 * 直出，不经后端，避免大文件占用应用线程。key 限小写字母/数字/下划线，防任意建行。
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
    return new DownloadStatDto(key, service.count(key));
  }

  @PostMapping("/{key}/hit")
  public DownloadStatDto hit(@PathVariable @Pattern(regexp = KEY_PATTERN) String key) {
    return new DownloadStatDto(key, service.increment(key));
  }
}
