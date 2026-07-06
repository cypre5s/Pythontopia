package com.pythontopia.mainline.test.question;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** 章测题面集合（对齐契约根对象 {@code {"items":[...]}}）。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionPaper(List<QuestionItem> items) {

  public List<QuestionItem> items() {
    return items == null ? List.of() : items;
  }

  public int totalScore() {
    return items().stream().mapToInt(QuestionItem::score).sum();
  }
}
