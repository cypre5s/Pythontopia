package com.pythontopia.feature.lecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 讲义阅读 ViewModel：列表 ↔ 阅读器。
 */
@HiltViewModel
class LectureViewModel @Inject constructor(
    private val repository: LectureRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LectureUiState())
    val state: StateFlow<LectureUiState> = _state.asStateFlow()

    init {
        loadList()
        selectChapter(1)
    }

    fun setMode(mode: LectureMode) {
        _state.update { it.copy(mode = mode) }
    }

    /** 选章并拉视频清单；自动选中首个可播放视频。 */
    fun selectChapter(chapter: Int) {
        _state.update { it.copy(chapter = chapter, videosLoading = true) }
        viewModelScope.launch {
            val videos = repository.chapterVideos(chapter)
            val first = videos.firstOrNull { it.available } ?: videos.firstOrNull()
            _state.update {
                it.copy(
                    videos = videos,
                    videosLoading = false,
                    selectedVideoId = first?.id,
                    videoUrl = first?.id?.let { id -> repository.videoUrl(id) },
                )
            }
        }
    }

    fun selectVideo(id: Long) {
        _state.update { it.copy(selectedVideoId = id, videoUrl = repository.videoUrl(id)) }
    }

    fun loadList() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val items = repository.listHandouts()
            _state.update {
                it.copy(
                    items = items,
                    loading = false,
                    error = if (items.isEmpty()) "还没有生成的讲义（去工坊生成，或确认已登录学生账号）" else null,
                )
            }
        }
    }

    fun openResource(id: Long) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.findOne(id)
                .onSuccess { resource ->
                    _state.update {
                        it.copy(
                            level = LectureLevel.READER,
                            selected = resource,
                            handout = repository.parseHandout(resource.content),
                            loading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = "讲义加载失败：${e.message ?: "网络异常"}") }
                }
        }
    }

    fun back() {
        _state.update { it.copy(level = LectureLevel.LIST, selected = null, handout = null) }
    }
}
