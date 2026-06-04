package com.pythontopia.game

import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * 玩家输入路由：实现 [InputProcessor]（外接键盘 / Chromebook） + 自绘屏幕左下角的虚拟摇杆
 * （Touchpad-style，dead-zone 8%，行为对齐 `docs/android/03-rendering-libgdx-vs-compose.md` §7）。
 *
 * 设计取舍：
 * - 内部状态全部存在普通字段（没有 GL handle），渲染线程读写；
 *   [resolveStep] 把 8 向输入降级成 4 向 dx/dy（与后端 PlayerStateDto 对齐），8 向动画选帧
 *   是 R-13 议题，这里先固化 4 向。
 * - 不实现 `Stage + Touchpad` actor，因为 R-12 demo 不引入 scene2d 依赖；自绘摇杆只需
 *   `ShapeRenderer.Filled`，足够直观。
 * - 触摸坐标由 LibGDX 以 `Gdx.input.x/y` 给出（屏幕像素，y 向下），本类不直接读 Gdx；
 *   外部调用方调用 [setPointer] / [clearPointer] 通报触摸状态，便于在 headless 测试中
 *   模拟手指。
 * - `keyDown` 仅用于 ESC / SPACE 这种"边沿触发"事件；持续移动靠 [isKeyHeld] 由调用方
 *   每帧调用 `Gdx.input.isKeyPressed` 决定，避免输入处理器吞按键事件。
 */
class InputController(
    private val viewportWidthProvider: () -> Int = { 0 },
    private val viewportHeightProvider: () -> Int = { 0 },
) : InputProcessor {

    private val keysHeld: MutableSet<Int> = mutableSetOf()
    private val pendingEdges: ArrayDeque<EdgeEvent> = ArrayDeque()

    /** 触摸状态：null 表示未触摸；非 null 表示 (x,y) 像素坐标（屏幕坐标，y 向下）。 */
    private var pointer: PointerSample? = null

    /** 摇杆中心和半径，单位像素；在 [updateLayout] 时确定。摇杆位置在屏幕左下区。 */
    private var joystickCenterX: Float = 0f
    private var joystickCenterY: Float = 0f
    private var joystickRadius: Float = DEFAULT_JOYSTICK_RADIUS

    /**
     * 根据屏幕宽高重算摇杆放置位置。请在每帧 render 前或 resize 时调用。
     */
    fun updateLayout() {
        val w = viewportWidthProvider()
        val h = viewportHeightProvider()
        if (w <= 0 || h <= 0) return
        joystickRadius = max(MIN_JOYSTICK_RADIUS, min(w, h) * JOYSTICK_RADIUS_RATIO)
        joystickCenterX = joystickRadius * 2f
        joystickCenterY = h - joystickRadius * 2f
    }

    /**
     * 手指按下/拖动时，由调用方在主输入轮询里调用。
     * 屏幕像素坐标（y 向下，与 `Gdx.input.x/y` 一致）。
     */
    fun setPointer(screenX: Int, screenY: Int) {
        pointer = PointerSample(screenX.toFloat(), screenY.toFloat())
    }

    /** 手指抬起时清空摇杆。 */
    fun clearPointer() {
        pointer = null
    }

    /**
     * 解析当前帧应当移动的 dx/dy（4 向）。键盘优先；键盘未按时退化到摇杆。
     *
     * @param keyboardSnapshot 一次性键状态快照，调用方用 `Gdx.input.isKeyPressed(...)` 填充；
     *   测试时可手工构造。
     */
    fun resolveStep(keyboardSnapshot: KeyboardSnapshot = KeyboardSnapshot.from(keysHeld)): IntPair {
        val keyboardStep = keyboardStep(keyboardSnapshot)
        return if (keyboardStep.isMoving) keyboardStep else joystickStep()
    }

    private fun keyboardStep(keyboardSnapshot: KeyboardSnapshot): IntPair {
        var dx = 0
        var dy = 0
        if (keyboardSnapshot.up) dy += 1
        if (keyboardSnapshot.down) dy -= 1
        if (keyboardSnapshot.left) dx -= 1
        if (keyboardSnapshot.right) dx += 1
        return if (dx != 0 || dy != 0) {
            IntPair(reduceTo4(dx), reduceTo4(dy, dropDx = dx != 0))
        } else {
            IntPair.ZERO
        }
    }

    private fun joystickStep(): IntPair {
        val sample = pointer ?: return IntPair.ZERO
        val w = viewportWidthProvider()
        val h = viewportHeightProvider()
        if (w <= 0 || h <= 0) return IntPair.ZERO
        val rawDx = sample.x - joystickCenterX
        val rawDyScreen = sample.y - joystickCenterY
        val distance = hypot(rawDx, rawDyScreen)
        val withinActiveRing = distance <= joystickRadius * JOYSTICK_MAX_DISTANCE_RATIO
        val outsideDeadzone = distance >= joystickRadius * DEADZONE_RATIO
        if (!withinActiveRing || !outsideDeadzone) return IntPair.ZERO

        val rawDyWorld = -rawDyScreen
        return if (abs(rawDx) > abs(rawDyWorld)) {
            IntPair(if (rawDx > 0) 1 else -1, 0)
        } else {
            IntPair(0, if (rawDyWorld > 0) 1 else -1)
        }
    }

    /**
     * 弹出一个边沿事件（如 ESC、SPACE）。常用于交互确认 / 暂停切换。
     * 调用一次取走一个，没有则返回 null。
     */
    fun pollEdge(): EdgeEvent? = pendingEdges.removeFirstOrNull()

    /**
     * 绘制虚拟摇杆（圆底 + 当前手指位置点）。
     * 调用方负责 `shape.begin(Filled)` / `shape.end()`。
     *
     * 注意：用 [ShapeRenderer.setColor]`(r,g,b,a)` 而非 `shape.color = X; shape.color.a = ...`，
     * 否则后者会修改 [PixelTheme] 单例 Color 对象的 alpha，污染其它 Screen 的渲染。
     */
    fun drawJoystick(shape: ShapeRenderer) {
        if (joystickRadius <= 0f) return
        val base = PixelTheme.gray100
        shape.setColor(base.r, base.g, base.b, JOYSTICK_BASE_ALPHA)
        shape.circle(joystickCenterX, joystickCenterY, joystickRadius)

        val sample = pointer
        if (sample != null) {
            val dx = (sample.x - joystickCenterX).coerceIn(-joystickRadius, joystickRadius)
            val dyScreen = (sample.y - joystickCenterY).coerceIn(-joystickRadius, joystickRadius)
            shape.color = PixelTheme.primary500
            shape.circle(joystickCenterX + dx, joystickCenterY + dyScreen, joystickRadius * 0.35f)
        }
    }

    fun isKeyHeld(keycode: Int): Boolean = keysHeld.contains(keycode)

    // --- InputProcessor ---

    override fun keyDown(keycode: Int): Boolean {
        keysHeld.add(keycode)
        when (keycode) {
            Input.Keys.SPACE, Input.Keys.ENTER -> pendingEdges.addLast(EdgeEvent.INTERACT)
            Input.Keys.ESCAPE -> pendingEdges.addLast(EdgeEvent.CANCEL)
        }
        return true
    }

    override fun keyUp(keycode: Int): Boolean {
        keysHeld.remove(keycode)
        return true
    }

    override fun keyTyped(character: Char): Boolean = false

    override fun touchDown(screenX: Int, screenY: Int, pointerId: Int, button: Int): Boolean {
        if (pointerId != 0) return false
        setPointer(screenX, screenY)
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointerId: Int): Boolean {
        if (pointerId != 0) return false
        setPointer(screenX, screenY)
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointerId: Int, button: Int): Boolean {
        if (pointerId != 0) return false
        clearPointer()
        return true
    }

    @Suppress("DEPRECATION")
    override fun touchCancelled(screenX: Int, screenY: Int, pointerId: Int, button: Int): Boolean {
        if (pointerId != 0) return false
        clearPointer()
        return true
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean = false

    override fun scrolled(amountX: Float, amountY: Float): Boolean = false

    private fun reduceTo4(value: Int, dropDx: Boolean = false): Int {
        if (value == 0) return 0
        if (dropDx) return 0
        return if (value > 0) 1 else -1
    }

    enum class EdgeEvent { INTERACT, CANCEL }

    data class PointerSample(val x: Float, val y: Float)

    data class IntPair(val dx: Int, val dy: Int) {
        val isMoving: Boolean
            get() = dx != 0 || dy != 0

        companion object {
            val ZERO: IntPair = IntPair(0, 0)
        }
    }

    /**
     * 当前帧键盘状态（4 向 + 2 边沿）。
     * 不直接持有 `Gdx.input`，让单测可以构造任意场景。
     */
    data class KeyboardSnapshot(
        val up: Boolean,
        val down: Boolean,
        val left: Boolean,
        val right: Boolean,
    ) {
        companion object {
            fun from(held: Set<Int>): KeyboardSnapshot = KeyboardSnapshot(
                up = held.contains(Input.Keys.W) || held.contains(Input.Keys.UP),
                down = held.contains(Input.Keys.S) || held.contains(Input.Keys.DOWN),
                left = held.contains(Input.Keys.A) || held.contains(Input.Keys.LEFT),
                right = held.contains(Input.Keys.D) || held.contains(Input.Keys.RIGHT),
            )

            val NONE: KeyboardSnapshot = KeyboardSnapshot(
                up = false, down = false, left = false, right = false,
            )
        }
    }

    companion object {
        const val DEFAULT_JOYSTICK_RADIUS: Float = 80f
        const val MIN_JOYSTICK_RADIUS: Float = 60f
        const val JOYSTICK_RADIUS_RATIO: Float = 0.12f
        const val JOYSTICK_MAX_DISTANCE_RATIO: Float = 1.4f
        const val DEADZONE_RATIO: Float = 0.18f
        const val JOYSTICK_BASE_ALPHA: Float = 0.35f
    }
}
