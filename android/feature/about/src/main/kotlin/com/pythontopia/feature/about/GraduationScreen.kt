package com.pythontopia.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/** 终局 · 毕业典礼（静态演出）：七章 Python 之旅圆满收尾。 */
@Composable
fun GraduationScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(PytSpace.s4)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PytSpace.s3),
    ) {
        Text("🎓", fontSize = 64.sp, modifier = Modifier.padding(top = PytSpace.s5))
        Text("毕业典礼", color = PytColors.primary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "恭喜你走通《Python语言基础》七章之旅！",
            color = PytColors.textPrimary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            "从计算思维到归纳抽象，你用真实的看、学、练、测，把一门课变成了自己的能力。" +
                "披上毕业袍，为这段 Pythontopia 冒险画上句点——也开启你真正的 Python 旅程。",
            color = PytColors.textMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = PytSpace.s3),
        )
        Text(
            "「学成出师，不是终点，而是你能独立解决问题的起点。」",
            color = PytColors.textHint,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = PytSpace.s3, start = PytSpace.s3, end = PytSpace.s3),
        )
        Text(
            "（达成条件：七章全部至少 1 星 + 通关第 7 章终极 BOSS）",
            color = PytColors.textHint,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = PytSpace.s4),
        )
    }
}
