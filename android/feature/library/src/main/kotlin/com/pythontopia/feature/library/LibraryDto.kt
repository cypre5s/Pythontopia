package com.pythontopia.feature.library

import kotlinx.serialization.Serializable

/**
 * 课程图书馆 DTO，对齐后端 CoursePackController（camelCase Java record）：
 *  - GET /api/course/chapters → [LibraryChapterDto]
 *  - GET /api/course/kcs?chapterId= → [LibraryKcDto]
 *  - GET /api/course/pages?kcCode= → [LibraryPageDto]（含 contentMarkdown 课件原文）
 */

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
