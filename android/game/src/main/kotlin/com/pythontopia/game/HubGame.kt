package com.pythontopia.game

import com.badlogic.gdx.Game

/**
 * Android-R12 demo 使用的 LibGDX [Game] 实现：仅含一张 hub 地图。
 *
 * 设计取舍：
 * - 不解析 frontend 的 Tiled JSON（[`town_hub.json`](frontend/public/assets/maps/town_hub.json)），
 *   改为程序化绘制 50×40 网格（草地 + 6 个建筑 block + 4 个边缘门洞 + 1 个 NPC 站点）。
 * - 玩家用纯色矩形渲染；像素 sprite 切片由 Android-R13+ 接入 TextureAtlas 时替换。
 * - 与 NPC 的交互检测放在 [HubGame.checkInteraction]，由 Compose 侧通过 [HubGameBridge] 读取。
 *
 * 该类不直接持有 Compose / Hilt 引用，保持 :game 模块对上层框架零依赖；交互信号经
 * [HubGameBridge] 单向输出给 Compose。
 */
class HubGame(
    private val bridge: HubGameBridge,
) : Game() {

    override fun create() {
        setScreen(HubScene(bridge))
    }
}
