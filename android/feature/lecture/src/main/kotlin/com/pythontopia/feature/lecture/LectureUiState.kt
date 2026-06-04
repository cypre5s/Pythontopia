package com.pythontopia.feature.lecture

enum class LectureLevel { LIST, READER }

data class LectureUiState(
    val level: LectureLevel = LectureLevel.LIST,
    val items: List<LectureListItemDto> = emptyList(),
    val selected: LectureResourceDto? = null,
    val handout: HandoutContent? = null,
    val loading: Boolean = false,
    val error: String? = null,
)
