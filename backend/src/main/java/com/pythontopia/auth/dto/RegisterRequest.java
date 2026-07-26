package com.pythontopia.auth.dto;

import com.pythontopia.auth.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * username 与 displayName 拒绝 HTML 元字符（&lt; &gt; &amp; " '），避免字段在其他端
 * （Android / 邮件通知 / OpenAPI 文档）被原样渲染触发 XSS；username 进一步限定为
 * 字母/数字/下划线/连字符/点。
 *
 * <p>{@code role} 是不可信输入：注册端点 permitAll，请求体可以自称任意角色。真正的准入判定在
 * {@code AuthService#register} 的白名单（仅 STUDENT / TEACHER，TEACHER 另需邀请码），此处的
 * {@code @NotNull} 只保证字段存在。
 */
public record RegisterRequest(
    @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "username only allows A-Z, a-z, 0-9, _, ., -")
        String username,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^[^<>&\"'`]+$", message = "displayName cannot contain HTML special chars")
        String displayName,
    @NotNull Role role,
    @Email @Size(max = 128) String email,
    String teacherInviteCode) {}
