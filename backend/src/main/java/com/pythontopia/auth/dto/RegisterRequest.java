package com.pythontopia.auth.dto;

import com.pythontopia.auth.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * R-13 安全加固：username + displayName 拒绝 HTML 元字符（&lt; &gt; &amp; "" '
 * 等），防止字段直传到其他端（Android / 邮件通知 / OpenAPI 文档）时被原样渲染
 * 触发 XSS。Web 前端 Vue 模板有兜底转义，但后端层依旧应该做净化。
 * username 进一步限制为：字母/数字/下划线/连字符/点，避开特殊字符。
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
