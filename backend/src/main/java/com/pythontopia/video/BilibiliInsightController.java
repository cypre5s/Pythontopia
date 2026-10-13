package com.pythontopia.video;

import com.pythontopia.common.web.AiBudgeted;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「B站视频概述 + 关键时间点」端点：针对学生问题概述某视频，并给出可点击跳转的关键时间点（时间戳）。
 *
 * <p>镜像 {@link BilibiliVideoController} 的结构：端点**保持鉴权**（不在 {@code SecurityConfig} 放行，
 * 由 {@code anyRequest().authenticated()} 兜底，axios 会带 JWT），无需改安全配置。取数实现隔离在
 * Python 微服务，本控制器仅经 {@link BilibiliInsightService} 代理；snake→camel 字段映射由
 * {@link BilibiliInsightResponse} 完成。
 */
@Validated
@RestController
@RequestMapping("/api/videos/bilibili/insight")
public class BilibiliInsightController {

  private final BilibiliInsightService service;

  public BilibiliInsightController(BilibiliInsightService service) {
    this.service = service;
  }

  @PostMapping
  @AiBudgeted
  public BilibiliInsightResponse insight(@Valid @RequestBody BilibiliInsightRequest request) {
    return service.insight(request);
  }
}
