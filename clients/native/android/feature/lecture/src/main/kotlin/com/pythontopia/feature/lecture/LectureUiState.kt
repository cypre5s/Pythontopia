package com.pythontopia.feature.lecture

/** 讲义屏导航层级：列表或阅读器。 */
enum class LectureLevel { LIST, READER }

/** 顶部模式：视频讲解（对齐 Web /lecture）/ 生成讲义阅读。 */
enum class LectureMode { VIDEO, HANDOUT }

/** 讲义列表/阅读器 + 视频讲解 UI 状态。 */
data class LectureUiState(
    val mode: LectureMode = LectureMode.VIDEO,
    val level: LectureLevel = LectureLevel.LIST,
    val items: List<LectureListItemDto> = emptyList(),
    val selected: LectureResourceDto? = null,
    val handout: HandoutContent? = null,
    val loading: Boolean = false,
    val error: String? = null,
    // ── 视频讲解 ──
    val chapter: Int = 1,
    val videos: List<CourseVideoDto> = emptyList(),
    val selectedVideoId: Long? = null,
    val videoUrl: String? = null,
    val videosLoading: Boolean = false,
)
