package com.pythontopia.auth.security;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pythontopia.security")
public record TeacherInviteProperties(String teacherInviteCode) {

  static final int MIN_CODE_CHARS = 16;

  private static final Set<String> KNOWN_PLACEHOLDER_CODES = Set.of("pytopia-teacher");

  private static final List<String> PLACEHOLDER_MARKERS =
      List.of(
          "replace-with", "replace_with", "change-me", "changeme", "placeholder", "your-invite");

  public TeacherInviteProperties {
    if (teacherInviteCode == null || teacherInviteCode.isBlank()) {
      throw new IllegalStateException("PYT_TEACHER_INVITE_CODE must be set");
    }
    teacherInviteCode = teacherInviteCode.trim();
    String normalized = teacherInviteCode.toLowerCase(Locale.ROOT);
    if (KNOWN_PLACEHOLDER_CODES.contains(normalized)
        || PLACEHOLDER_MARKERS.stream().anyMatch(normalized::contains)) {
      throw new IllegalStateException(
          "PYT_TEACHER_INVITE_CODE is a publicly known placeholder value; generate a random code");
    }
    if (teacherInviteCode.length() < MIN_CODE_CHARS) {
      throw new IllegalStateException(
          "PYT_TEACHER_INVITE_CODE must be at least " + MIN_CODE_CHARS + " characters");
    }
  }
}
