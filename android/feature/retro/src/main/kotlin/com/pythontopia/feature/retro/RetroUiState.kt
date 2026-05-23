package com.pythontopia.feature.retro

/**
 * 复盘大厅 UI 状态。
 *
 *  - [currentReport]：当前主视图展示的周报告，可能是本周（首次加载）或用户从 chip 切换到的历史周；
 *  - [pastReports]：除当前展示之外的历史周列表，按 weekIndex 降序，长度 = `pastWeeks` 参数；
 *  - [thisWeekIndex]：本周的 weekIndex，用于 chip 上标记「本周」徽标；
 *  - [loading] / [error]：标准异步状态，error 用 String 而非 AppError，避免 UI 层
 *    与 [com.pythontopia.core.common.AppError] 紧耦合，文案直接展示给学生。
 *
 * 设计取舍：不引入 `UiState<RetroReport>` sealed wrapper，
 * 因为 retro 同屏需要并存「主报告 loading + 历史列表已就绪」两种局部状态，
 * sealed 反而不利于分块刷新。
 */
data class RetroUiState(
    val loading: Boolean = false,
    val currentReport: RetroReport? = null,
    val pastReports: List<RetroReport> = emptyList(),
    val thisWeekIndex: Long? = null,
    val error: String? = null,
)
