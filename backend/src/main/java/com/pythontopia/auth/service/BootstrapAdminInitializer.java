package com.pythontopia.auth.service;

import com.pythontopia.auth.repository.UserRepository;
import com.pythontopia.auth.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次部署自举管理员。
 *
 * <p>自助注册默认关闭（{@code pythontopia.auth.registration-enabled=false}）后，全新的数据库里没有任何
 * 账号，也就没人能登录管理后台创建第一个管理员。本组件在应用就绪、Flyway 迁移完成后按 {@code .env}
 * 配置确保库中存在一个 ADMIN：
 *
 * <ul>
 *   <li>库中已存在任意 ADMIN：直接跳过，绝不改动既有账号、绝不重置口令（幂等，重复部署无副作用）。
 *   <li>配置的用户名已存在但角色不是 ADMIN：提升为 ADMIN，并自增 token_version 让旧 JWT 立即失效。
 *   <li>否则按配置新建 ADMIN 账号。
 * </ul>
 *
 * <p>两个配置项都为空时完全跳过，因此本地开发、以及已有初始化数据的实例行为不变。配置了但填错
 * （只填一半、口令太短）只记录错误日志，不阻断启动——管理员仍可用 SQL 兜底创建。
 */
@Component
public class BootstrapAdminInitializer {
  private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

  /** 与自助注册的最小口令长度保持一致，避免出现比注册还弱的默认管理员口令。 */
  private static final int MIN_PASSWORD_LENGTH = 8;

  private static final String DEFAULT_DISPLAY_NAME = "管理员";

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final String username;
  private final String password;
  private final String displayName;

  public BootstrapAdminInitializer(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${pythontopia.auth.bootstrap-admin.username:}") String username,
      @Value("${pythontopia.auth.bootstrap-admin.password:}") String password,
      @Value("${pythontopia.auth.bootstrap-admin.display-name:" + DEFAULT_DISPLAY_NAME + "}")
          String displayName) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.username = username == null ? "" : username.trim();
    this.password = password == null ? "" : password.trim();
    this.displayName =
        displayName == null || displayName.isBlank() ? DEFAULT_DISPLAY_NAME : displayName.trim();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void ensureAdminAccount() {
    if (username.isEmpty() && password.isEmpty()) {
      log.debug("未配置初始管理员（PYT_BOOTSTRAP_ADMIN_USERNAME / _PASSWORD），跳过自举");
      return;
    }
    if (username.isEmpty() || password.isEmpty()) {
      log.error(
          "初始管理员配置不完整：PYT_BOOTSTRAP_ADMIN_USERNAME 与 PYT_BOOTSTRAP_ADMIN_PASSWORD 必须同时设置，已跳过自举");
      return;
    }
    if (password.length() < MIN_PASSWORD_LENGTH) {
      log.error(
          "初始管理员口令不足 {} 位，已跳过自举；请修改 PYT_BOOTSTRAP_ADMIN_PASSWORD 后重启 backend",
          MIN_PASSWORD_LENGTH);
      return;
    }
    try {
      if (userRepository.existsAdmin()) {
        log.info("库中已存在管理员账号，跳过初始管理员自举");
        return;
      }
      if (userRepository.existsByUsername(username)) {
        userRepository.promoteToAdmin(username);
        log.warn("账号 {} 已存在，已提升为 ADMIN（旧 JWT 已失效）", username);
        return;
      }
      userRepository.save(
          username, displayName, passwordEncoder.encode(password), null, Role.ADMIN);
      log.warn("已创建初始管理员账号 {}，请登录后立即修改口令并妥善保管 .env", username);
    } catch (RuntimeException exception) {
      // 自举失败不应阻断整个实例启动：手工 SQL 仍可创建管理员。
      log.error("初始管理员自举失败，可用 SQL 手工创建管理员", exception);
    }
  }
}
