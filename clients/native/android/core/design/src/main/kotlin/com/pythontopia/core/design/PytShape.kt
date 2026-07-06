package com.pythontopia.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 设计令牌 · 圆角：近方角像素风；[pixel]/[xs] 最方，[pill] 为胶囊。 */
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
