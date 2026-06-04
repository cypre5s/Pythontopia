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
) {
    /** 顶部面包屑文案。 */
    val crumb: String
        get() = when (level) {
            LibraryLevel.CHAPTERS -> "课程图书馆"
            LibraryLevel.KCS -> selectedChapter?.let { "第 ${it.orderIndex} 章 · ${it.title}" } ?: "知识点"
            LibraryLevel.PAGES -> selectedKc?.kcName ?: "课件页"
            LibraryLevel.READER -> selectedPage?.let { "P${it.pageNo}" } ?: "阅读"
        }
}
