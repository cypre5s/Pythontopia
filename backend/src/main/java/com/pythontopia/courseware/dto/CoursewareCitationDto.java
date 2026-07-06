package com.pythontopia.courseware.dto;

/** 课件问答引用（可跳转的课件页）。chapterId 可空。 */
public record CoursewareCitationDto(int pageId, int pageNo, Integer chapterId, String quote) {}
