package com.pythontopia.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Pythontopia 设计令牌 · 圆角。
 *
 * 像素小镇偏好「近方角」：容器圆角小而克制，越大的容器才略带圆角。
 * 既有 [sm] / [md] / [lg] 取值保持不变（[PythontopiaTheme] 与组件已按此校准），仅追加更像素的 [pixel] / [xs]
 * 与更大的 [xl] 以及胶囊 [pill]，方便标签 / chip / 木框统一取用。
 */
object PytShape {
    /** 纯方角——最像素的容器（木牌 / 进度槽）。 */
    val pixel = RoundedCornerShape(0.dp)
    val xs = RoundedCornerShape(2.dp)
    val sm = RoundedCornerShape(4.dp)
    val md = RoundedCornerShape(6.dp)
    val lg = RoundedCornerShape(10.dp)
    val xl = RoundedCornerShape(14.dp)

    /** 胶囊（标签 / chip / 头像框）。 */
    val pill = RoundedCornerShape(percent = 50)
}
