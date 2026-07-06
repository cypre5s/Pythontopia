package com.pythontopia.feature.dialog

/** 对话选项；后端尚未返回节点时由本地 DemoDialogTree 驱动。 */
data class DialogChoice(
    val label: String,
    val nextNodeId: String?,
    val setFlagKey: String? = null,
    val setFlagValue: String? = null,
)

data class DialogNode(
    val id: String,
    val text: String,
    val choices: List<DialogChoice>,
)

object DemoDialogTree {

    const val NPC_KEY: String = "mayor_py"
    const val ROOT_NODE_ID: String = "greet"

    /** R-14：中央小镇 12 个主线 NPC（与 data/npcs/index.json 对齐）。 */
    val allNpcKeys: List<String> = listOf(
        "mayor_py",
        "librarian_doc",
        "coach_q",
        "sage_review",
        "professor",
        "student_a",
        "workshop_master",
        "arena_guard",
        "mine_keeper",
        "foreman_bug",
        "class_president",
        "captain_def",
    )

    private val npcDisplayNames: Map<String, String> = mapOf(
        "mayor_py" to "镇长 Py",
        "librarian_doc" to "馆长 Doc",
        "coach_q" to "教务 Q",
        "sage_review" to "复盘师 Sage",
        "professor" to "教授",
        "student_a" to "学生 A",
        "workshop_master" to "工坊师傅",
        "arena_guard" to "竞技场守卫",
        "mine_keeper" to "矿洞守矿人",
        "foreman_bug" to "矿头 Bug",
        "class_president" to "班长",
        "captain_def" to "船长 Def",
    )

    val nodes: Map<String, DialogNode> = listOf(
        DialogNode(
            id = "greet",
            text = "欢迎来到 Pythontopia，{{playerName}}！我是镇长 Py，Python 之父 Guido 的化身。",
            choices = listOf(
                DialogChoice("请告诉我这里有什么？", nextNodeId = "introduce_town"),
                DialogChoice("我应该从哪里开始？", nextNodeId = "where_to_start"),
                DialogChoice("聊聊我的进度", nextNodeId = "chat_progress"),
                DialogChoice("再见", nextNodeId = null),
            ),
        ),
        DialogNode(
            id = "introduce_town",
            text = "R-14 起，所有学习入口都收进这座中央小镇：图书馆、工坊、训练场、竞技场、" +
                "复盘大厅和 Bug 矿洞入口都在步行范围内，不必再跨图奔波。",
            choices = listOf(
                DialogChoice(
                    label = "好的，我去探索一下",
                    nextNodeId = null,
                ),
            ),
        ),
        DialogNode(
            id = "where_to_start",
            text = "你刚来不久，建议先在喷泉广场熟悉走位，再去图书馆找 Doc 看课件，" +
                "或在训练场提交第一道练习。",
            choices = listOf(
                DialogChoice(
                    label = "我懂了",
                    nextNodeId = null,
                ),
            ),
        ),
        DialogNode(
            id = "chat_progress",
            text = "你已经走进原生小镇。Python 之路无非两步：写下去、再写下去。",
            choices = listOf(
                DialogChoice(
                    label = "谢谢镇长",
                    nextNodeId = null,
                ),
                DialogChoice(
                    label = "记一下：我已经开始探索",
                    nextNodeId = null,
                    setFlagKey = "android_native_hub_played",
                    setFlagValue = "true",
                ),
            ),
        ),
    ).associateBy { it.id }

    private val genericTrees: Map<String, Map<String, DialogNode>> =
        allNpcKeys.filterNot { it == NPC_KEY }.associateWith { key -> createGenericTree(key) }

    fun nodesFor(npcKey: String): Map<String, DialogNode> =
        if (npcKey == NPC_KEY) nodes else genericTrees[npcKey] ?: createGenericTree(npcKey)

    fun displayNameFor(npcKey: String): String = npcDisplayNames[npcKey] ?: npcKey

    private fun createGenericTree(npcKey: String): Map<String, DialogNode> {
        val name = displayNameFor(npcKey)
        val focus = focusFor(npcKey)
        return listOf(
            DialogNode(
                id = ROOT_NODE_ID,
                text = "$name：我负责$focus。先完成真实练习或阅读证据，再回来找我推进任务。",
                choices = listOf(
                    DialogChoice("我现在该做什么？", nextNodeId = "focus"),
                    DialogChoice("稍后再来", nextNodeId = null),
                ),
            ),
            DialogNode(
                id = "focus",
                text = "$name：打开对应原生功能页，提交服务端记录过的证据。小镇只认真实学习痕迹。",
                choices = listOf(
                    DialogChoice(
                        label = "明白了",
                        nextNodeId = null,
                        setFlagKey = "${npcKey}_met",
                        setFlagValue = "true",
                    ),
                ),
            ),
        ).associateBy { it.id }
    }

    private fun focusFor(npcKey: String): String = when (npcKey) {
        "librarian_doc" -> "课件引用和资料阅读"
        "coach_q", "arena_guard" -> "练习挑战与闯关"
        "sage_review" -> "周复盘和遗忘提醒"
        "foreman_bug", "mine_keeper" -> "Bug 矿洞和失败提交修复"
        "workshop_master" -> "个性化资源生成"
        "class_president", "captain_def" -> "班级任务和协作路线"
        else -> "Python 学习路径"
    }
}
