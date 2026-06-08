package com.pythontopia.feature.lecture

/** 讲义屏导航层级：列表或阅读器。 */
enum class LectureLevel { LIST, READER }

/** 讲义列表/阅读器 UI 状态。 */
data class LectureUiState(
    val level: LectureLevel = LectureLevel.LIST,
    val items: List<LectureListItemDto> = emptyList(),
    val selected: LectureResourceDto? = null,
    val handout: HandoutContent? = null,
    val loading: Boolean = false,
    val error: String? = null,
)
