package com.pythontopia.feature.fields

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** KcTile 支撑逻辑单测：status 映射、调色板、a11y 文案、DTO 转换（无 Compose 运行时）。 */
class KcTileTest {

    @Test
    fun `statusFor maps mastery 0_05 to SEED`() {
        assertEquals(KcStatus.SEED, KcTileState.statusFor(0.05, null))
    }

    @Test
    fun `statusFor maps mastery at growing threshold to GROWING`() {
        assertEquals(KcStatus.GROWING, KcTileState.statusFor(KcTileState.GROWING_THRESHOLD, null))
        assertEquals(KcStatus.GROWING, KcTileState.statusFor(0.5, null))
        assertEquals(KcStatus.GROWING, KcTileState.statusFor(0.79, null))
    }

    @Test
    fun `statusFor maps mastery at mature threshold to MATURE`() {
        assertEquals(KcStatus.MATURE, KcTileState.statusFor(KcTileState.MATURE_THRESHOLD, null))
        assertEquals(KcStatus.MATURE, KcTileState.statusFor(0.95, null))
    }

    @Test
    fun `statusFor honors explicit HARVESTED override`() {
        assertEquals(KcStatus.HARVESTED, KcTileState.statusFor(1.0, "HARVESTED"))
        assertEquals(KcStatus.HARVESTED, KcTileState.statusFor(1.0, "harvested"))
    }

    @Test
    fun `statusFor falls back to mastery when explicit is unknown`() {
        assertEquals(KcStatus.MATURE, KcTileState.statusFor(0.85, "FOO"))
        assertEquals(KcStatus.SEED, KcTileState.statusFor(0.0, ""))
    }

    @Test
    fun `palette returns distinct fill colors for all 4 status`() {
        val fills = KcStatus.entries.map { FieldsPalette.statusFill(it) }
        val unique = fills.toSet()
        assertEquals(KcStatus.entries.size, unique.size, "4 个状态必须有 4 种不同填充色")
    }

    @Test
    fun `palette returns distinct inner colors for all 4 status`() {
        val inners = KcStatus.entries.map { FieldsPalette.statusInner(it) }
        assertEquals(KcStatus.entries.size, inners.toSet().size, "4 个状态必须有 4 种不同内层色")
    }

    @Test
    fun `tileContentDescription renders SEED label`() {
        val tile = KcTileState("PY43-KC-001", "种子样例", 1, 0.05, KcStatus.SEED)
        val desc = tileContentDescription(tile, isSelected = false, isPending = false)
        assertTrue(desc.contains("种子"))
        assertTrue(desc.contains("PY43-KC-001"))
        assertFalse(desc.contains("已选"))
        assertFalse(desc.contains("正在操作"))
    }

    @Test
    fun `tileContentDescription renders GROWING label`() {
        val tile = KcTileState("PY43-KC-010", "幼苗样例", 2, 0.5, KcStatus.GROWING)
        val desc = tileContentDescription(tile, isSelected = false, isPending = false)
        assertTrue(desc.contains("幼苗"))
        assertTrue(desc.contains("50%"))
    }

    @Test
    fun `tileContentDescription renders MATURE label with selected prefix`() {
        val tile = KcTileState("PY43-KC-031", "成熟样例", 4, 0.9, KcStatus.MATURE)
        val desc = tileContentDescription(tile, isSelected = true, isPending = false)
        assertTrue(desc.startsWith("已选"))
        assertTrue(desc.contains("成熟"))
        assertTrue(desc.contains("90%"))
    }

    @Test
    fun `tileContentDescription renders HARVESTED label with pending suffix`() {
        val tile = KcTileState("PY43-KC-065", "收获样例", 7, 1.0, KcStatus.HARVESTED)
        val desc = tileContentDescription(tile, isSelected = false, isPending = true)
        assertTrue(desc.contains("已收获"))
        assertTrue(desc.endsWith("正在操作"))
    }

    @Test
    fun `KcMasteryDto toTileState computes status from mastery when explicit is null`() {
        val dto = KcMasteryDto("PY43-KC-031", "for循环", 0.9, 4, null)
        val tile = dto.toTileState()
        assertEquals(KcStatus.MATURE, tile.status)
        assertEquals("PY43-KC-031", tile.kcCode)
        assertEquals(4, tile.chapterOrder)
        assertEquals(0.9, tile.mastery)
    }

    @Test
    fun `KcMasteryDto toTileState clamps mastery into 0_0 to 1_0`() {
        val low = KcMasteryDto("PY43-KC-001", "中国计算机发展成就", -0.5, 1, null).toTileState()
        val high = KcMasteryDto("PY43-KC-065", "面向对象编程的特征", 2.5, 7, null).toTileState()
        assertEquals(0.0, low.mastery)
        assertEquals(1.0, high.mastery)
    }

    @Test
    fun `KcSeedCatalog contains exactly 65 entries covering 7 chapters`() {
        val entries = KcSeedCatalog.entries
        assertEquals(KcSeedCatalog.EXPECTED_KC_COUNT, entries.size)
        val chapters = entries.map { it.chapterOrder }.toSet()
        assertEquals((1..7).toSet(), chapters, "必须覆盖第 1-7 章全部 7 章")
    }

    @Test
    fun `KcSeedCatalog byCode lookup returns null for unknown code`() {
        assertNotNull(KcSeedCatalog.byCode("PY43-KC-001"))
        assertNull(KcSeedCatalog.byCode("PY43-KC-999"))
    }

    @Test
    fun `displaySeason and displayWeather map known enums to Chinese`() {
        assertEquals("春", displaySeason("SPRING"))
        assertEquals("夏", displaySeason("summer"))
        assertEquals("秋", displaySeason("FALL"))
        assertEquals("冬", displaySeason("WINTER"))
        assertEquals("CUSTOM", displaySeason("CUSTOM"))

        assertEquals("晴", displayWeather("CLEAR"))
        assertEquals("雨", displayWeather("rain"))
        assertEquals("雪", displayWeather("SNOWY"))
        assertEquals("阴", displayWeather("CLOUDY"))
    }

    @Test
    fun `FieldsUiState aggregate counters reflect tiles status`() {
        val tiles = listOf(
            KcTileState("PY43-KC-001", "a", 1, 0.0, KcStatus.SEED),
            KcTileState("PY43-KC-002", "b", 1, 0.5, KcStatus.GROWING),
            KcTileState("PY43-KC-003", "c", 1, 0.9, KcStatus.MATURE),
            KcTileState("PY43-KC-004", "d", 1, 1.0, KcStatus.HARVESTED),
            KcTileState("PY43-KC-005", "e", 1, 0.9, KcStatus.MATURE),
        )
        val state = FieldsUiState(tiles = tiles)
        assertEquals(5, state.totalCount)
        assertEquals(1, state.growingCount)
        assertEquals(2, state.matureCount)
        assertEquals(1, state.harvestedCount)
        assertEquals(KcStatus.MATURE, state.tileFor("PY43-KC-003")?.status)
        assertNull(state.tileFor(null))
    }

    @Test
    fun `tile fill color differs from inner color for visual contrast`() {
        // GROWING：fill 与 inner 须有色相对比
        assertNotEquals(
            FieldsPalette.statusFill(KcStatus.GROWING),
            FieldsPalette.statusInner(KcStatus.GROWING),
        )
        // MATURE 同样须有色相对比
        assertNotEquals(
            FieldsPalette.statusFill(KcStatus.MATURE),
            FieldsPalette.statusInner(KcStatus.MATURE),
        )
    }
}
