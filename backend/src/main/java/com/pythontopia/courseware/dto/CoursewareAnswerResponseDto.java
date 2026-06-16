package com.pythontopia.courseware.dto;

import java.util.List;

/** 课件问答响应：LLM 作答 Markdown + 可跳转页码引用；无证据时 noEvidence=true。 */
public record CoursewareAnswerResponseDto(
    String answerMd, List<CoursewareCitationDto> citations, boolean noEvidence) {}
