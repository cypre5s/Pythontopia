package com.pythontopia.feature.fields

/**
 * Fields 屏幕的 UI state，是 ViewModel 唯一向 Compose 层暴露的不可变数据契约。
 *
 *  - [tiles]：65 KC 网格的当前快照；空列表表示 ViewModel 尚未拉到数据，UI 应显示骨架；
 *  - [isLoading]：网络刷新中，禁用 HUD 与单格点击；
 *  - [pendingActionKc]：正在执行 water/harvest 的 KC code（同一时间只允许 1 个，避免后端竞态）；
 *  - [selectedKc]：玩家点开的 KC 详情面板对象，决定操作栏（浇水/收获）显示哪条；
 *  - [errorMessage]：上一次操作失败的简短提示，UI 渲染 1 次后由 ViewModel 清除；
 *  - [season] / [weather]：HUD 顶栏展示，由 ViewModel 在 init 写入默认值，与 Hub `WorldClock` 解耦。
 */
data class FieldsUiState(
    val tiles: List<KcTileState> = emptyList(),
    val isLoading: Boolean = false,
    val pendingActionKc: String? = null,
    val selectedKc: String? = null,
    val errorMessage: String? = null,
    val season: String = DEFAULT_SEASON,
    val weather: String = DEFAULT_WEATHER,
) {
    val totalCount: Int get() = tiles.size

    val harvestedCount: Int get() = tiles.count { it.status == KcStatus.HARVESTED }

    val matureCount: Int get() = tiles.count { it.status == KcStatus.MATURE }

    val growingCount: Int get() = tiles.count { it.status == KcStatus.GROWING }

    fun tileFor(kcCode: String?): KcTileState? =
        kcCode?.let { code -> tiles.firstOrNull { it.kcCode == code } }

    val isBusy: Boolean get() = isLoading || pendingActionKc != null

    companion object {
        const val DEFAULT_SEASON: String = "SPRING"
        const val DEFAULT_WEATHER: String = "CLEAR"
    }
}
