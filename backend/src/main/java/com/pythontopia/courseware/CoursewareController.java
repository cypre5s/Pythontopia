package com.pythontopia.courseware;

import com.pythontopia.courseware.dto.CoursewareAnswerRequestDto;
import com.pythontopia.courseware.dto.CoursewareAnswerResponseDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课件问答（LLM 作答 + 页码跳转引用）对外端点：{@code POST /api/courseware/answer}。
 *
 * <p>与仅教师可用的 {@code /api/rag/query}（返回原文片段）不同，本端点面向<b>学生</b>：
 * 任意已认证用户可用，graph 侧只依据命中课件作答并做页码防幻觉过滤。
 */
@RestController
@RequestMapping("/api/courseware")
public class CoursewareController {

  private final CoursewareGraphClient client;

  public CoursewareController(CoursewareGraphClient client) {
    this.client = client;
  }

  @PostMapping("/answer")
  public CoursewareAnswerResponseDto answer(@Valid @RequestBody CoursewareAnswerRequestDto request) {
    return client.answer(request);
  }
}
