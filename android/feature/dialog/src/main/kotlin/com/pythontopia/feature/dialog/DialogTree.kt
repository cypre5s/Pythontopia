package com.pythontopia.feature.dialog

/**
 * Android-R12 demo 内嵌的 NPC 对话树。
 *
 * 数据完全对齐 `data/npcs/mayor_py/dialog.json`（rootNodeId="greet" + 4 主分支），
 * 把 frontend 的模板插值（如 `{{playerName}}`）退化为 demo 文案，把 effects 中
 * type=friendship/delta=N 映射为后端 [NpcDialogEffectDto.type="FRIENDSHIP_DELTA"]，
 * type=set_flag 映射为 SET_FLAG，复杂的 give_item / quest_recommend 留给 Android-R13+
 * 接入 :feature:path 时再消化。
 *
 * 抽到独立文件方便：
 *  1. 单测 [DialogTreeTest] 直接断言节点拓扑；
 *  2. 后续替换为从 `assets/data/npcs/<key>/dialog.json` 读取真正数据时只动加载入口。
 */
data class DialogChoice(
    val label: String,
    val nextNodeId: String?,
    val friendshipDelta: Int = 0,
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
            text = "小镇分为 5 个区域：中央 hub、北农场、南矿洞、东校园、西港口。" +
                "Android-R12 demo 只先做 hub；其它 4 张地图属于 Android-R13+ 范围。",
            choices = listOf(
                DialogChoice(
                    label = "好的，我去探索一下",
                    nextNodeId = null,
                    friendshipDelta = 1,
                ),
            ),
        ),
        DialogNode(
            id = "where_to_start",
            text = "你刚来不久，建议先在小镇里熟悉走位，再去农场让田老带你解锁第一个 KC。",
            choices = listOf(
                DialogChoice(
                    label = "我懂了",
                    nextNodeId = null,
                    friendshipDelta = 2,
                ),
            ),
        ),
        DialogNode(
            id = "chat_progress",
            text = "你已经踏出 demo 第一步：成功打开了 hub。Python 之路无非两步：写下去、再写下去。",
            choices = listOf(
                DialogChoice(
                    label = "谢谢镇长",
                    nextNodeId = null,
                    friendshipDelta = 1,
                ),
                DialogChoice(
                    label = "记一下：我玩过 R-12 demo",
                    nextNodeId = null,
                    friendshipDelta = 0,
                    setFlagKey = "android_r12_demo_played",
                    setFlagValue = "true",
                ),
            ),
        ),
    ).associateBy { it.id }
}
