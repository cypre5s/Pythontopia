package com.pythontopia.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** 教师自助注册邀请码必须在应用启动期阻断空值、弱值和仓库公开占位值。 */
class TeacherInvitePropertiesTest {

  private static final String STRONG_INVITE = "7ec856ee4f514a66ad5bf1f4f7a9c193";

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("空邀请码拒绝")
  void rejectsBlankInviteCode(String inviteCode) {
    assertThatThrownBy(() -> new TeacherInviteProperties(inviteCode))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PYT_TEACHER_INVITE_CODE");
  }

  @Test
  @DisplayName("长度不足的邀请码拒绝")
  void rejectsShortInviteCode() {
    assertThatThrownBy(() -> new TeacherInviteProperties("too-short"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "pytopia-teacher",
        "PYTOPIA-TEACHER",
        "replace-with-teacher-invite",
        "change-me-teacher-invite",
        "teacher-placeholder-code"
      })
  @DisplayName("仓库公开占位值拒绝")
  void rejectsPublicPlaceholderInviteCodes(String inviteCode) {
    assertThatThrownBy(() -> new TeacherInviteProperties(inviteCode))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("placeholder")
        .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain(inviteCode));
  }

  @Test
  @DisplayName("随机邀请码通过")
  void acceptsRandomInviteCode() {
    assertThatCode(() -> new TeacherInviteProperties(STRONG_INVITE)).doesNotThrowAnyException();
  }
}
