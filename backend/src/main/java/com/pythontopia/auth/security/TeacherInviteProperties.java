package com.pythontopia.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pythontopia.security")
public record TeacherInviteProperties(String teacherInviteCode) {}
