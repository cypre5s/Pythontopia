package com.pythontopia.feature.fields

import androidx.compose.ui.graphics.Color

/**
 * Fields feature 的本地像素调色板。
 *
 * 设计原则：与 `:core:design/PytColors` 全局 token 保持色相一致（暖琥珀 + 深棕背景），
 * 但作物 4 状态需要更强的色彩区分度，因此在 feature 内单独定义而非污染全局 token。
 *
 * 4 状态色彩教学意图：
 *  - SEED（深棕翻土色）：刚撒种、未发芽，提醒"需要先浇 1-2 次水"；
 *  - GROWING（嫩绿）：幼苗在长，与"成长"心智图同色；
 *  - MATURE（金黄）：成熟可收，与 PytColors.warning 同色但更鲜艳，吸引玩家点击收获；
 *  - HARVESTED（中性灰棕）：已收获留空地，弱化避免与活跃作物混淆。
 */
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
