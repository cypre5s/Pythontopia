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
 * Fields 屏幕 ViewModel：负责把 [FieldsRepository] 拉到的 65 KC 网格暴露给 Compose 层，
 * 并把 UI 层"浇水 / 收获 / 选择 KC"三类意图翻译成仓库调用。
 *
 *  - 启动时自动 [refresh] 一次；UI 完全由 [state] 单一 [StateFlow] 驱动；
 *  - 操作期间通过 [FieldsUiState.pendingActionKc] 互斥，避免双击触发同一 KC 同时浇水 + 收获；
 *  - 业务规则在 [water] / [harvest] 内做最小校验：只有 MATURE 才允许收获，HARVESTED 不允许再浇水。
 *
 * MVVM 而非 MVI：状态字段 6 个、动作 4 个，未触发 ADR-001 §1 决策 10 走 MVI 的复杂度门槛。
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

    /**
     * 拉一次完整网格。重复触发会忽略当前 inflight 请求结果，让最后一次刷新 win。
     */
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

    /** UI 层点击格子 → 高亮 + 弹出操作栏；再次点击同一 KC 取消选择。 */
    fun selectKc(kcCode: String?) {
        _state.update { current ->
            val next = if (current.selectedKc == kcCode) null else kcCode
            current.copy(selectedKc = next, errorMessage = null)
        }
    }

    /**
     * 浇水动作。前置校验：
     *  - 必须存在 inflight 槽位空闲（[FieldsUiState.pendingActionKc] == null）；
     *  - 目标 KC 已在网格中（防止意外传入未知 code）；
     *  - 状态非 HARVESTED（已收获的田不可再浇水，玩家应等 reseed）。
     *
     * 成功后用 Repository 返回值替换网格中对应 KC；失败则写 errorMessage 让 UI 提示。
     */
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

    /**
     * 收获动作。仅 MATURE 状态合法；否则写 errorMessage 提示玩家等成熟。
     */
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

    /** UI 层渲染完 errorMessage 后调用，避免下一次重组继续看到旧错误。 */
    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

/** 在网格内用 kcCode 替换单个瓦片，保持顺序稳定，避免重组时整列重排。 */
private fun List<KcTileState>.replace(updated: KcTileState): List<KcTileState> =
    map { if (it.kcCode == updated.kcCode) updated else it }
