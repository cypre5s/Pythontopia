package com.pythontopia.feature.fields

/**
 * 知识田 UI 状态；[pendingActionKc] 同时只允许一个 water/harvest 在飞，
 * [isBusy] 合并 loading 与 pending。
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
