package com.pythontopia.feature.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pythontopia.core.design.PytColors
import com.pythontopia.core.design.PytSpace

/** 操作指南（触屏精简版）：按主题列出「是什么 / 与闭环关系 / 关键要点」。 */
@Composable
fun GuideScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(PytSpace.s3),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        item {
            Column {
                Text("操作指南", color = PytColors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Pythontopia 的核心是「看 → 学 → 练 → 测」学习闭环。下面按主题速览各场所怎么用。",
                    color = PytColors.textHint,
                    fontSize = 12.sp,
                )
            }
        }
        items(GUIDE_SECTIONS) { section -> GuideCard(section) }
    }
}

@Composable
private fun GuideCard(section: GuideSection) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderSoft),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3), verticalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
            Text(section.navTitle, color = PytColors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(section.title, color = PytColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(section.summary, color = PytColors.textMuted, fontSize = 13.sp)
            Surface(color = PytColors.bgSoft, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "闭环：${section.loop}",
                    color = PytColors.info,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(PytSpace.s2),
                )
            }
            section.points.forEach { point ->
                Text("· $point", color = PytColors.textPrimary, fontSize = 13.sp)
            }
        }
    }
}
