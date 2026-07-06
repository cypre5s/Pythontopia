package com.pythontopia.coursepack.dto;

import com.pythontopia.coursepack.domain.CourseExample;
import java.util.List;

public record CourseExampleDto(
    Long id,
    Long packId,
    String kcCode,
    String title,
    String code,
    String explanationMarkdown,
    List<Long> pageRefs) {

  public static CourseExampleDto from(CourseExample example) {
    return new CourseExampleDto(
        example.id(),
        example.packId(),
        example.kcCode(),
        example.title(),
        example.code(),
        example.explanationMarkdown(),
        example.pageRefs() == null ? List.of() : List.of(example.pageRefs()));
  }
}
