package com.pythontopia.video;

import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「B站精选讲解」检索端点（方案 A）：按任务/章节/KC 关键词检索 B站视频，返回外部资源卡片。
 *
 * <p>镜像 {@link com.pythontopia.coursepack.controller.CourseVideoController} 的结构。端点**保持鉴权**
 * （不在 {@code SecurityConfig} 放行；axios 会带 JWT），无需改安全配置。取数实现在 Python 微服务，
 * 本控制器仅经 {@link BilibiliVideoService} 代理。
 */
@Validated
@RestController
@RequestMapping("/api/videos/bilibili")
public class BilibiliVideoController {

  private final BilibiliVideoService service;

  public BilibiliVideoController(BilibiliVideoService service) {
    this.service = service;
  }

  @GetMapping
  public List<BilibiliVideoDto> search(
      @RequestParam String keyword,
      @RequestParam(required = false, defaultValue = "8") int limit) {
    return service.search(keyword, limit);
  }
}
