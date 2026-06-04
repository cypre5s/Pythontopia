package com.pythontopia.game

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue

/**
 * 解析 Tiled（`town_hub.json` 等）的瓦片层并在 LibGDX y-up 世界里渲染。
 *
 * 坐标系对齐：Tiled 数据是 y-down（第 0 行在最上＝北），而本项目 Android 端
 * （[WorldMapData] / [PlayerActor] / 出口）沿用 y-up（tileY 越大越靠北）。
 * 因此解析时把每一行翻转：`grid[ty][tx] = data[(height-1-ty)*width + tx]`，
 * 渲染、碰撞、精灵坐标从此统一在 y-up，无需再做二次翻转。
 *
 * 碰撞从 `buildings` 瓦片层推导（非空格＝实心），与可见建筑像素级一致；
 * 解析失败时回退为全草地（仅 [WorldMapData] 的矩形建筑兜底），保证永不崩。
 */
class TiledMapVisual private constructor(
    val key: String,
    val width: Int,
    val height: Int,
    val usesDungeon: Boolean,
    private val layers: List<TileLayer>,
    private val solid: Array<BooleanArray>,
) {
    val pixelWidth: Int = width * TILE
    val pixelHeight: Int = height * TILE
    val layerCount: Int get() = layers.size

    fun isSolid(tileX: Int, tileY: Int): Boolean {
        if (!inBounds(tileX, tileY)) return true
        return solid[tileY][tileX]
    }

    private fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    /**
     * 渲染可见范围内的全部瓦片层（按 ground→buildings→decoration 顺序）。
     * 调用方负责 `batch.begin()/end()` 与设置投影矩阵；这里只发 draw。
     * [minTileX]..[maxTileX] / [minTileY]..[maxTileY] 为视锥剔除范围（含端点）。
     */
    fun render(
        batch: SpriteBatch,
        assets: GameAssets,
        minTileX: Int,
        minTileY: Int,
        maxTileX: Int,
        maxTileY: Int,
        fromLayer: Int = 0,
        toLayer: Int = layers.size,
    ) {
        val x0 = minTileX.coerceIn(0, width - 1)
        val x1 = maxTileX.coerceIn(0, width - 1)
        val y0 = minTileY.coerceIn(0, height - 1)
        val y1 = maxTileY.coerceIn(0, height - 1)
        val end = toLayer.coerceAtMost(layers.size)
        for (i in fromLayer.coerceAtLeast(0) until end) renderLayer(batch, assets, layers[i], x0, y0, x1, y1)
    }

    private fun renderLayer(batch: SpriteBatch, assets: GameAssets, layer: TileLayer, x0: Int, y0: Int, x1: Int, y1: Int) {
        for (ty in y0..y1) {
            val rowArr = layer.tiles[ty]
            val worldY = (ty * TILE).toFloat()
            for (tx in x0..x1) drawTile(batch, assets, rowArr[tx], tx, worldY)
        }
    }

    private fun drawTile(batch: SpriteBatch, assets: GameAssets, idx: Int, tx: Int, worldY: Float) {
        if (idx < 0) return
        val region = assets.tileRegion(usesDungeon, idx) ?: return
        batch.draw(region, (tx * TILE).toFloat(), worldY, TILE.toFloat(), TILE.toFloat())
    }

    class TileLayer(val name: String, val tiles: Array<IntArray>)

    companion object {
        const val TILE = 16

        /** 从内置 assets 读地图；失败返回 null（调用方回退程序化渲染）。 */
        @Suppress("TooGenericExceptionCaught")
        fun fromInternal(mapKey: String, fileName: String): TiledMapVisual? = try {
            val text = Gdx.files.internal("maps/$fileName").readString("UTF-8")
            parse(mapKey, text)
        } catch (t: RuntimeException) {
            // 故意宽容：地图缺失/损坏不应崩游戏，回退程序化草地。
            Gdx.app?.error("TiledMapVisual", "load failed for $fileName: ${t.message}")
            null
        }

        /** 纯解析（可在测试里传 JSON 字符串）。 */
        fun parse(mapKey: String, json: String): TiledMapVisual {
            val root: JsonValue = JsonReader().parse(json)
            val width = root.getInt("width")
            val height = root.getInt("height")
            val tilesetsNode = root.get("tilesets")
            val hasTileset = tilesetsNode != null && tilesetsNode.size > 0
            val firstGid = if (hasTileset) tilesetsNode[0].getInt("firstgid", 1) else 1
            val tilesetImage = if (hasTileset) tilesetsNode[0].getString("image", "") else ""
            val usesDungeon = tilesetImage.contains("dungeon", ignoreCase = true) ||
                mapKey.contains("mine", ignoreCase = true)

            val layers = mutableListOf<TileLayer>()
            var node = root.get("layers")?.child
            while (node != null) {
                parseTileLayer(node, width, height, firstGid)?.let { layers += it }
                node = node.next
            }
            val buildingsLayer = layers.firstOrNull { it.name.equals("buildings", ignoreCase = true) }

            val solid = Array(height) { y ->
                BooleanArray(width) { x -> (buildingsLayer?.tiles?.get(y)?.get(x) ?: -1) >= 0 }
            }
            return TiledMapVisual(mapKey, width, height, usesDungeon, layers, solid)
        }

        private fun parseTileLayer(layer: JsonValue, width: Int, height: Int, firstGid: Int): TileLayer? {
            if (layer.getString("type", "") != "tilelayer") return null
            val data = layer.get("data")?.asIntArray() ?: return null
            if (data.size < width * height) return null
            val name = layer.getString("name", "")
            return TileLayer(name, toFlippedGrid(data, width, height, firstGid))
        }

        private fun toFlippedGrid(data: IntArray, width: Int, height: Int, firstGid: Int): Array<IntArray> =
            Array(height) { ty ->
                val srcRow = (height - 1 - ty) * width
                IntArray(width) { tx ->
                    val gid = data[srcRow + tx]
                    if (gid <= 0) -1 else gid - firstGid
                }
            }
    }
}
