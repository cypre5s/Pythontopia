package com.pythontopia.feature.expedition

/**
 * 无尽远征「学习增益」(relics) 与「概念事件」内容池
 * （移植 Web `rogueRelics.ts` / `rogueEvents.ts`）。
 */

data class RogueRelic(
    val id: String,
    val name: String,
    val icon: String,
    val desc: String,
)

object RogueRelics {
    val ALL: List<RogueRelic> = listOf(
        RogueRelic("sturdy_notes", "厚实笔记", "🛡", "体力上限 +1，并立即回 1 点（更耐打）。"),
        RogueRelic("calm_mind", "沉静心法", "🧘", "答错少扣 1 点体力（至少扣 1）。"),
        RogueRelic("combo_scholar", "连题学者", "🔥", "通关得分 +20%。"),
        RogueRelic("second_wind", "复盘回气", "💧", "答错时气势减半，而非清零。"),
        RogueRelic("focus_lens", "专注透镜", "🔍", "营地复盘额外回 1 点体力。"),
    )

    fun byId(id: String): RogueRelic? = ALL.firstOrNull { it.id == id }
}

enum class RogueEventChoice { STUDY, STEADY, REVIEW }

data class RogueEventChoiceMeta(
    val key: RogueEventChoice,
    val label: String,
    val icon: String,
    val desc: String,
)

object RogueEvents {
    val CHOICES: List<RogueEventChoiceMeta> = listOf(
        RogueEventChoiceMeta(RogueEventChoice.STUDY, "迎难钻研", "🔬", "挑战一道更难的题（难度 +1），通关得分 ×1.5。"),
        RogueEventChoiceMeta(RogueEventChoice.STEADY, "稳扎稳打", "🎯", "按当前难度挑战一题，常规得分。"),
        RogueEventChoiceMeta(RogueEventChoice.REVIEW, "就地复盘", "📖", "不开打：回 1 点体力，拿少量分直接通过。"),
    )

    private val SCENARIOS: List<String> = listOf(
        "岔路口立着一块旧木牌，刻着一道关于「{kc}」的谜题。",
        "一位赶路的学徒卡在「{kc}」上，停下来向你请教。",
        "废墟石台上摊着半张「{kc}」的手稿，字迹斑驳待补。",
        "机关门上的符文与「{kc}」同源，似乎在等一个答案。",
        "篝火旁的老学者聊起「{kc}」，邀你切磋两手。",
    )

    fun pickScenario(kcName: String, rng: () -> Double = Math::random): String {
        val tpl = SCENARIOS.getOrNull((rng() * SCENARIOS.size).toInt()) ?: SCENARIOS.first()
        return tpl.replace("{kc}", kcName)
    }
}

/**
 * KC 友好显示名。Android 端不复用 :feature:fields 的 65 条标题目录（避免 feature→feature 依赖），
 * 暂以「KC NN」短名呈现；后续可下沉一份共享 KC 名录到 :core:data 再增强。
 */
fun kcDisplayName(code: String?): String {
    if (code.isNullOrBlank()) return "知识点"
    val num = Regex("""PY43-KC-(\d+)""").find(code)?.groupValues?.getOrNull(1)
    return if (num != null) "KC $num" else code
}
