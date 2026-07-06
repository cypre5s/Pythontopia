package com.pythontopia.feature.fields

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
 * 知识田 ViewModel：暴露 65 KC 网格，处理选择/浇水/收获；
 * [pendingActionKc] 互斥防并发，仅 MATURE 可收获、HARVESTED 不可再浇水。
 */
@HiltViewModel
class FieldsViewModel @Inject constructor(
    private val repository: FieldsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FieldsUiState())
    val state: StateFlow<FieldsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** 重复触发时以最后一次刷新结果为准。 */
    fun refresh() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val tiles = runCatching { repository.fetchKcGrid() }
                .getOrElse { emptyList() }
            _state.update {
                it.copy(
                    tiles = tiles,
                    isLoading = false,
                    errorMessage = if (tiles.isEmpty()) {
                        "暂无法加载知识田，请稍后下拉重试"
                    } else {
                        null
                    },
                )
            }
        }
    }

    /** 点击切换选中 KC；再次点击同一格取消选择。 */
    fun selectKc(kcCode: String?) {
        _state.update { current ->
            val next = if (current.selectedKc == kcCode) null else kcCode
            current.copy(selectedKc = next, errorMessage = null)
        }
    }

    /** 浇水；[pendingActionKc] 占用中或 HARVESTED 时拒绝。 */
    fun water(kcCode: String) {
        val current = _state.value
        if (current.pendingActionKc != null) return
        val existing = current.tileFor(kcCode) ?: return
        if (existing.status == KcStatus.HARVESTED) {
            _state.update { it.copy(errorMessage = "${existing.title} 已收获，等待下一轮播种") }
            return
        }
        _state.update { it.copy(pendingActionKc = kcCode, errorMessage = null) }
        viewModelScope.launch {
            val updated = runCatching { repository.waterKc(kcCode) }.getOrNull()
            _state.update { snapshot ->
                snapshot.copy(
                    pendingActionKc = null,
                    tiles = if (updated == null) {
                        snapshot.tiles
                    } else {
                        snapshot.tiles.replace(updated)
                    },
                    errorMessage = if (updated == null) "浇水失败，请检查网络后重试" else null,
                )
            }
        }
    }

    /** 收获；仅 MATURE 合法。 */
    fun harvest(kcCode: String) {
        val current = _state.value
        if (current.pendingActionKc != null) return
        val existing = current.tileFor(kcCode) ?: return
        if (existing.status != KcStatus.MATURE) {
            _state.update {
                it.copy(errorMessage = "${existing.title} 尚未成熟（需要 mastery ≥ 0.8）")
            }
            return
        }
        _state.update { it.copy(pendingActionKc = kcCode, errorMessage = null) }
        viewModelScope.launch {
            val updated = runCatching { repository.harvestKc(kcCode) }.getOrNull()
            _state.update { snapshot ->
                snapshot.copy(
                    pendingActionKc = null,
                    tiles = if (updated == null) {
                        snapshot.tiles
                    } else {
                        snapshot.tiles.replace(updated)
                    },
                    errorMessage = if (updated == null) "收获失败，请检查网络后重试" else null,
                )
            }
        }
    }

    /** 清除一次性 [errorMessage]。 */
    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

/** 按 kcCode 替换单格，保持列表顺序稳定。 */
private fun List<KcTileState>.replace(updated: KcTileState): List<KcTileState> =
    map { if (it.kcCode == updated.kcCode) updated else it }
