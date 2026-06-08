package com.pythontopia.feature.fields

import androidx.compose.ui.graphics.Color

/** Fields 本地调色板：色相对齐 `PytColors`，KC 四态单独设色以保证区分度。 */
internal object FieldsPalette {

    val tileSeed = Color(0xFF6B4F2C)
    val tileSeedInner = Color(0xFF8A6C2C)
    val tileGrowing = Color(0xFF5FBF6C)
    val tileGrowingInner = Color(0xFFB8E6BE)
    val tileMature = Color(0xFFE0A83A)
    val tileMatureInner = Color(0xFFFFD680)
    val tileHarvested = Color(0xFF3D2E1C)
    val tileHarvestedInner = Color(0xFF5A4622)

    val tileBorder = Color(0xFF1A1410)
    val tileBorderActive = Color(0xFFFEF7E3)

    val hudBackground = Color(0xFF2A2018)
    val hudAccent = Color(0xFFC9A86B)

    val actionWater = Color(0xFF5A9BBD)
    val actionHarvest = Color(0xFFE0A83A)

    fun statusFill(status: KcStatus): Color = when (status) {
        KcStatus.SEED -> tileSeed
        KcStatus.GROWING -> tileGrowing
        KcStatus.MATURE -> tileMature
        KcStatus.HARVESTED -> tileHarvested
    }

    fun statusInner(status: KcStatus): Color = when (status) {
        KcStatus.SEED -> tileSeedInner
        KcStatus.GROWING -> tileGrowingInner
        KcStatus.MATURE -> tileMatureInner
        KcStatus.HARVESTED -> tileHarvestedInner
    }
}
