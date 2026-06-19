package com.pythontopia.feature.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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

/** 致谢：按 license 分组列出已审计的 CC0 / CC-BY / CC-BY-SA / OFL 素材署名。 */
@Composable
fun CreditsScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PytColors.bgPrimary)
            .padding(PytSpace.s3),
        verticalArrangement = Arrangement.spacedBy(PytSpace.s2),
    ) {
        item {
            Column {
                Text("致谢 · 素材署名", color = PytColors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Pythontopia 仅使用已审计的 CC0 / CC-BY / CC-BY-SA / OFL 素材。完整声明见仓库 LICENSE-ART.md。",
                    color = PytColors.textHint,
                    fontSize = 12.sp,
                )
            }
        }
        LICENSE_GROUPS.forEach { group ->
            val entries = CREDITS.filter { it.license == group.license }
            if (entries.isNotEmpty()) {
                item { LicenseGroupCard(group, entries) }
            }
        }
    }
}

@Composable
private fun LicenseGroupCard(group: LicenseGroup, entries: List<CreditEntry>) {
    Surface(
        color = PytColors.bgElevated,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(2.dp, PytColors.borderStrong),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(PytSpace.s3), verticalArrangement = Arrangement.spacedBy(PytSpace.s2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
                Text(group.title, color = PytColors.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (group.requiresAttribution) Tag("需署名", PytColors.warning)
                if (group.shareAlike) Tag("传染 SA", PytColors.danger)
            }
            Text(group.note, color = PytColors.textHint, fontSize = 11.sp)
            entries.forEach { entry -> CreditRow(entry) }
        }
    }
}

@Composable
private fun CreditRow(entry: CreditEntry) {
    Surface(color = PytColors.bgSoft, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(PytSpace.s2), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(PytSpace.s1)) {
                Text(
                    entry.name,
                    color = PytColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Tag(entry.category, PytColors.info)
            }
            Text("作者：${entry.authors.joinToString("、")}", color = PytColors.textMuted, fontSize = 11.sp)
            Text("来源：${entry.source} · 用于：${entry.usedFor}", color = PytColors.textHint, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Tag(text: String, fg: androidx.compose.ui.graphics.Color) {
    Surface(color = PytColors.bgSunken, shape = RoundedCornerShape(4.dp)) {
        Text(text, color = fg, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
