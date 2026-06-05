package com.pythontopia.core.design

import androidx.compose.ui.unit.dp

/**
 * Pythontopia 设计令牌 · 间距（4dp 网格）。
 *
 * 既有 [s1]–[s6] 取值保持不变（被各 feature 广泛引用），仅追加 [s0]（零间距占位）与
 * [s7] / [s8] 两档更大的屏级留白，方便分区布局统一节奏。
 */
object PytSpace {
    val s0 = 0.dp
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 24.dp
    val s6 = 32.dp
    val s7 = 48.dp
    val s8 = 64.dp
}
