package com.pythontopia.auth.security;

/**
 * 登录态主体。{@code tokenVersion} 对应 {@code users.token_version}，用于把「签名有效」与
 * 「会话仍然有效」区分开：改密 / 停用 / 手工吊销时自增该列，旧 token 携带的旧代次即失配。
 */
public record CurrentUserPrincipal(Long userId, String username, Role role, int tokenVersion) {

  /** 兼容既有调用点：未显式给出会话代次时按 0（{@code users.token_version} 默认值）处理。 */
  public CurrentUserPrincipal(Long userId, String username, Role role) {
    this(userId, username, role, 0);
  }
}
