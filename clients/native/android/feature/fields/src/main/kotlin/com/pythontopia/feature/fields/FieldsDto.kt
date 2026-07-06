package com.pythontopia.feature.fields

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/learner/mastery` 单条；[status] 缺省时由 [KcTileState.statusFor] 按 mastery 推导。 */
@Serializable
data class KcMasteryDto(
    @SerialName("kcCode") val kcCode: String,
    @SerialName("title") val title: String = "",
    @SerialName("masteryScore") val mastery: Double = 0.0,
    @SerialName("chapterOrder") val chapterOrder: Int = 0,
    @SerialName("status") val status: String? = null,
)

/** KC 四态；阈值见 [KcTileState.statusFor]，HARVESTED 须后端显式枚举。 */
enum class KcStatus { SEED, GROWING, MATURE, HARVESTED }

/** 单格 KC UI 状态；由 [KcMasteryDto.toTileState] 转换，不直接序列化回后端。 */
data class KcTileState(
    val kcCode: String,
    val title: String,
    val chapterOrder: Int,
    val mastery: Double,
    val status: KcStatus,
) {
    companion object {
        /** 优先尊重显式 [status]；否则按 mastery 区间映射，避免高 mastery 误判 HARVESTED。 */
        fun statusFor(mastery: Double, explicit: String?): KcStatus {
            explicit?.let { raw ->
                runCatching { return KcStatus.valueOf(raw.uppercase()) }
            }
            return when {
                mastery >= MATURE_THRESHOLD -> KcStatus.MATURE
                mastery >= GROWING_THRESHOLD -> KcStatus.GROWING
                else -> KcStatus.SEED
            }
        }

        /** 成熟门槛 mastery ≥ 0.8。 */
        const val MATURE_THRESHOLD: Double = 0.8

        /** 发芽门槛 mastery ≥ 0.2。 */
        const val GROWING_THRESHOLD: Double = 0.2
    }
}

fun KcMasteryDto.toTileState(): KcTileState = KcTileState(
    kcCode = kcCode,
    title = title,
    chapterOrder = chapterOrder,
    mastery = mastery.coerceIn(0.0, 1.0),
    status = KcTileState.statusFor(mastery, status),
)

/** packId=43 共 65 KC 种子目录；后端缺项时补 title/chapterOrder，条目数须与课程包一致。 */
internal object KcSeedCatalog {

    val entries: List<KcSeed> = listOf(
        KcSeed("PY43-KC-001", "中国计算机发展成就", 1),
        KcSeed("PY43-KC-002", "计算工具的演变", 1),
        KcSeed("PY43-KC-003", "计算思维的方法与特征", 1),
        KcSeed("PY43-KC-004", "计算思维的概念与定义", 1),
        KcSeed("PY43-KC-005", "计算机发展历程与趋势", 1),
        KcSeed("PY43-KC-006", "计算的概念", 1),
        KcSeed("PY43-KC-007", "计算科学与计算机科学", 1),
        KcSeed("PY43-KC-008", "Python开发环境配置与程序运行", 2),
        KcSeed("PY43-KC-009", "Python语言概述", 2),
        KcSeed("PY43-KC-010", "变量与运算符", 2),
        KcSeed("PY43-KC-011", "基本数据类型", 2),
        KcSeed("PY43-KC-012", "基础函数", 2),
        KcSeed("PY43-KC-013", "控制台输入与数据类型转换", 2),
        KcSeed("PY43-KC-014", "程序的运行方式", 2),
        KcSeed("PY43-KC-015", "程序设计语言发展历史", 2),
        KcSeed("PY43-KC-016", "程序运行原理", 2),
        KcSeed("PY43-KC-017", "0和1的计算思维", 3),
        KcSeed("PY43-KC-018", "format()方法的格式控制", 3),
        KcSeed("PY43-KC-019", "Python数值类型与运算", 3),
        KcSeed("PY43-KC-020", "Unicode编码与字符函数", 3),
        KcSeed("PY43-KC-021", "二进制四则运算", 3),
        KcSeed("PY43-KC-022", "字符串处理方法", 3),
        KcSeed("PY43-KC-023", "字符串格式化", 3),
        KcSeed("PY43-KC-024", "字符串索引与切片", 3),
        KcSeed("PY43-KC-025", "整数位数提取", 3),
        KcSeed("PY43-KC-026", "文字信息编码", 3),
        KcSeed("PY43-KC-027", "汉字编码体系", 3),
        KcSeed("PY43-KC-028", "第三方库的使用", 3),
        KcSeed("PY43-KC-029", "进位计数制与二进制", 3),
        KcSeed("PY43-KC-030", "进制转换", 3),
        KcSeed("PY43-KC-031", "for循环与遍历", 4),
        KcSeed("PY43-KC-032", "while循环与循环分支结合", 4),
        KcSeed("PY43-KC-033", "分支结构", 4),
        KcSeed("PY43-KC-034", "分支语句嵌套", 4),
        KcSeed("PY43-KC-035", "多条件判断与逻辑运算", 4),
        KcSeed("PY43-KC-036", "循环嵌套", 4),
        KcSeed("PY43-KC-037", "循环控制语句", 4),
        KcSeed("PY43-KC-038", "顺序结构", 4),
        KcSeed("PY43-KC-039", "Random模块与列表", 5),
        KcSeed("PY43-KC-040", "列表基本操作", 5),
        KcSeed("PY43-KC-041", "字典在实际问题中的应用", 5),
        KcSeed("PY43-KC-042", "字典基本操作", 5),
        KcSeed("PY43-KC-043", "字典类型", 5),
        KcSeed("PY43-KC-044", "序列基本操作", 5),
        KcSeed("PY43-KC-045", "序列类型", 5),
        KcSeed("PY43-KC-046", "集合的删除操作", 5),
        KcSeed("PY43-KC-047", "集合类型", 5),
        KcSeed("PY43-KC-048", "Python文件操作", 6),
        KcSeed("PY43-KC-049", "基于文件的数据分析", 6),
        KcSeed("PY43-KC-050", "文件写入操作", 6),
        KcSeed("PY43-KC-051", "文件系统基础", 6),
        KcSeed("PY43-KC-052", "文件读取与数据处理", 6),
        KcSeed("PY43-KC-053", "词频统计", 6),
        KcSeed("PY43-KC-054", "lambda函数", 7),
        KcSeed("PY43-KC-055", "pyinstaller打包", 7),
        KcSeed("PY43-KC-056", "Python包", 7),
        KcSeed("PY43-KC-057", "Python库与pip工具", 7),
        KcSeed("PY43-KC-058", "主程序与__name__变量", 7),
        KcSeed("PY43-KC-059", "函数参数高级用法", 7),
        KcSeed("PY43-KC-060", "函数变量的作用域", 7),
        KcSeed("PY43-KC-061", "函数递归", 7),
        KcSeed("PY43-KC-062", "模块与导入", 7),
        KcSeed("PY43-KC-063", "模块化编程", 7),
        KcSeed("PY43-KC-064", "类和对象基础", 7),
        KcSeed("PY43-KC-065", "面向对象编程的特征", 7),
    )

    init {
        check(entries.size == EXPECTED_KC_COUNT) {
            "KcSeedCatalog 必须严格 65 条（与 Alethicode packId=43 对齐），实际 ${entries.size}"
        }
    }

    fun byCode(code: String): KcSeed? = entries.firstOrNull { it.kcCode == code }

    const val EXPECTED_KC_COUNT: Int = 65
}

/** Catalog 内部静态条目；仅作 fallback 数据源，避免业务层意外当作 mutable state。 */
internal data class KcSeed(
    val kcCode: String,
    val title: String,
    val chapterOrder: Int,
)
