package com.pythontopia.auth.domain;

import com.pythontopia.auth.security.Role;

/**
 * users 表的只读投影。
 *
 * <p>{@code status} 与 {@code tokenVersion} 是会话吊销的两个开关：前者停用整个账号，后者自增一次即
 * 让该用户全部已签发 JWT 立刻作废（登录、刷新与每次请求鉴权都会比对）。
 */
public record UserAccount(
    Long id,
    String username,
    String displayName,
    String passwordHash,
    String email,
    Role role,
    String status,
    int tokenVersion) {

  public static final String STATUS_ACTIVE = "ACTIVE";

  public boolean isActive() {
    return STATUS_ACTIVE.equals(status);
  }
}
