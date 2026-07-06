package com.pythontopia.feature.library

import kotlinx.serialization.Serializable

/** 课程图书馆 DTO，对齐后端 CoursePackController（camelCase）；pages 含 contentMarkdown 课件原文。 */

@Serializable
data class LibraryChapterDto(
    val id: Long,
    val orderIndex: Int = 0,
    val title: String = "",
    val summaryMarkdown: String? = null,
)

@Serializable
data class LibraryKcDto(
    val id: Long,
    val kcCode: String,
    val kcName: String,
    val chapterId: Long? = null,
    val chapterOrder: Int? = null,
    val difficulty: String? = null,
    val summaryMarkdown: String? = null,
)

@Serializable
data class LibraryPageDto(
    val id: Long,
    val documentId: Long? = null,
    val chapterId: Long? = null,
    val pageNo: Int = 0,
    val title: String? = null,
    val contentMarkdown: String? = null,
    val excerpt: String? = null,
)

/** 课件问答（面向学生）：`POST /api/courseware/answer`，LLM 依据命中课件作答并给可跳转页码。 */
@Serializable
data class CoursewareAnswerRequest(
    val query: String,
    val kcCodes: List<String>? = null,
    val topK: Int? = null,
)

@Serializable
data class CoursewareCitationDto(
    val pageId: Int = 0,
    val pageNo: Int = 0,
    val chapterId: Int? = null,
    val quote: String = "",
)

@Serializable
data class CoursewareAnswerResponseDto(
    val answerMd: String = "",
    val citations: List<CoursewareCitationDto> = emptyList(),
    val noEvidence: Boolean = false,
)
