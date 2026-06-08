package com.pythontopia.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 课程图书馆钻取导航（章→KC→页→阅读）；每级懒加载，[back] 逐级返回。 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        loadChapters()
    }

    fun loadChapters() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val chapters = repository.chapters()
            _state.update {
                it.copy(
                    chapters = chapters,
                    loading = false,
                    error = if (chapters.isEmpty()) "暂无课程章节（请确认已登录且后端在线）" else null,
                )
            }
        }
    }

    fun openChapter(chapter: LibraryChapterDto) {
        _state.update {
            it.copy(level = LibraryLevel.KCS, selectedChapter = chapter, loading = true, error = null, kcs = emptyList())
        }
        viewModelScope.launch {
            val kcs = repository.kcs(chapter.id)
            _state.update { it.copy(kcs = kcs, loading = false) }
        }
    }

    fun openKc(kc: LibraryKcDto) {
        _state.update {
            it.copy(level = LibraryLevel.PAGES, selectedKc = kc, loading = true, error = null, pages = emptyList())
        }
        viewModelScope.launch {
            val pages = repository.pages(kc.kcCode)
            _state.update {
                it.copy(
                    pages = pages,
                    loading = false,
                    error = if (pages.isEmpty()) "该知识点暂无关联课件页" else null,
                )
            }
        }
    }

    fun openPage(page: LibraryPageDto) {
        _state.update { it.copy(level = LibraryLevel.READER, selectedPage = page) }
    }

    fun back() {
        _state.update {
            when (it.level) {
                LibraryLevel.READER -> it.copy(level = LibraryLevel.PAGES, selectedPage = null)
                LibraryLevel.PAGES -> it.copy(level = LibraryLevel.KCS, selectedKc = null, error = null)
                LibraryLevel.KCS -> it.copy(level = LibraryLevel.CHAPTERS, selectedChapter = null, error = null)
                LibraryLevel.CHAPTERS -> it
            }
        }
    }
}
