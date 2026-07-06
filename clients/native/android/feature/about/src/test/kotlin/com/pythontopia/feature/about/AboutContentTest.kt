package com.pythontopia.feature.about

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AboutContentTest {

    @Test
    fun `每条素材的 license 都在 license 分组里（致谢可正确分组）`() {
        val known = LICENSE_GROUPS.map { it.license }.toSet()
        val unknown = CREDITS.map { it.license }.filterNot { it in known }
        assertTrue(unknown.isEmpty(), "存在未归类 license：$unknown")
    }

    @Test
    fun `CC-BY 与 SA 素材标记为需署名（AGENTS_md 强制）`() {
        LICENSE_GROUPS.filter { it.license.startsWith("CC-BY") || it.license == "OFL-1.1" }
            .forEach { assertTrue(it.requiresAttribution, "${it.license} 应需署名") }
        assertTrue(LICENSE_GROUPS.first { it.license == "CC-BY-SA 3.0" }.shareAlike)
    }

    @Test
    fun `致谢与指南内容非空且字段完整`() {
        assertFalse(CREDITS.isEmpty())
        CREDITS.forEach { assertTrue(it.name.isNotBlank() && it.authors.isNotEmpty()) }
        assertEquals(12, GUIDE_SECTIONS.size)
        GUIDE_SECTIONS.forEach {
            assertTrue(it.summary.isNotBlank() && it.loop.isNotBlank() && it.points.isNotEmpty())
        }
    }
}
