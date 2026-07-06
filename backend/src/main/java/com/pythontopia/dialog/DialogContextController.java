package com.pythontopia.dialog;

import com.pythontopia.auth.security.CurrentUserPrincipal;
import com.pythontopia.common.web.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话模板真实数据上下文接口。
 *
 * <p>{@code GET /api/dialog/context}：登录学习者拉取本人维度的对话渲染上下文，
 * 包括玩家名 / profile 类型 / 全量 stats key-value。
 *
 * <p>任何字段缺数据都不会抛 500，全部由 {@link DialogContextService} 兜底成合理字面值。
 */
@RestController
@RequestMapping("/api/dialog")
public class DialogContextController {

  private final DialogContextService service;

  public DialogContextController(DialogContextService service) {
    this.service = service;
  }

  @GetMapping("/context")
  public DialogContextDto context(@CurrentUser CurrentUserPrincipal user) {
    return service.buildContext(user.userId(), user.username());
  }
}
