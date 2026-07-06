package com.pythontopia.feature.library

/** 钻取层级：章 → KC → 页 → 阅读器。 */
enum class LibraryLevel { CHAPTERS, KCS, PAGES, READER }

data class LibraryUiState(
    val level: LibraryLevel = LibraryLevel.CHAPTERS,
    val chapters: List<LibraryChapterDto> = emptyList(),
    val kcs: List<LibraryKcDto> = emptyList(),
    val pages: List<LibraryPageDto> = emptyList(),
    val selectedChapter: LibraryChapterDto? = null,
    val selectedKc: LibraryKcDto? = null,
    val selectedPage: LibraryPageDto? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** 当前页的 PDF 渲染图（后端 PNG）；null 时阅读器降级到 markdown 文本。 */
    val pageImage: androidx.compose.ui.graphics.ImageBitmap? = null,
    val pageImageLoading: Boolean = false,
    // ── 课件问答（RAG Q&A） ──
    val askQuery: String = "",
    val asking: Boolean = false,
    val answerMd: String = "",
    val citations: List<CoursewareCitationDto> = emptyList(),
    val noEvidence: Boolean = false,
    val askError: String? = null,
) {
    val crumb: String
        get() = when (level) {
            LibraryLevel.CHAPTERS -> "课程图书馆"
            LibraryLevel.KCS -> selectedChapter?.let { "第 ${it.orderIndex} 章 · ${it.title}" } ?: "知识点"
            LibraryLevel.PAGES -> selectedKc?.kcName ?: "课件页"
            LibraryLevel.READER -> selectedPage?.let { "P${it.pageNo}" } ?: "阅读"
        }
}
