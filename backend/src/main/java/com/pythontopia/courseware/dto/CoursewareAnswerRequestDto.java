package com.pythontopia.courseware.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 课件问答请求（前端 → 后端，camelCase）。 */
public record CoursewareAnswerRequestDto(
    @NotBlank @Size(max = 512) String query, List<String> kcCodes, Integer topK) {}
