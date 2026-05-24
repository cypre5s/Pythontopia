package com.pythontopia.feature.arena

data class ChapterTowerLevel(
    val id: String,
    val chapter: Int,
    val index: Int,
    val kind: String,
    val title: String,
    val hint: String,
    val kcCodes: List<String>,
    val difficulty: Int,
    val cleared: Boolean = false,
    val unlocked: Boolean = false,
)

data class ChapterTowerUiState(
    val selectedChapter: Int = 1,
    val levels: List<ChapterTowerLevel> = emptyList(),
    val selectedLevelId: String? = null,
    val chapterCleared: Int = 0,
    val chapterTotal: Int = 10,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)
