---
version: 1.0
date: 2026-05-24
status: Draft
audience: Android 架构师 / 游戏程序员 / Compose UI 工程师
supersedes: 无
related:
  - clients/native/android/docs/ADR-001-tech-selection.md
  - docs/game-tilemap-conventions.md
  - frontend/src/game/scenes/WorldScene.ts
---

# 03 · Pythontopia Android 渲染分层规范：LibGDX 与 Compose 的职责切分

## 1. 文档定位

本文档锁定 **Android 客户端的双层渲染架构**：用 LibGDX 1.13.1 承担像素世界的实时渲染与输入，用 Jetpack Compose（Material 3，Compose BOM 2025.04.00）承担所有教学面板、表单、对话与系统级 UI。两层之间通过 `bridge + ViewModel` 单向解耦，不允许互相直接引用对方的类。

类比 Web 端：

| Web 端 | Android 端 | 备注 |
| --- | --- | --- |
| Phaser 3 | LibGDX 1.13.1 | 游戏循环 + tilemap + 输入 |
| Vue 3 + Pinia | Compose + ViewModel + Flow | 面板 UI + 状态管理 |
| `WorldCanvas.vue` 单 Game 实例 | `HubLibGdxFragment` 单 `Game` 实例 | 全局只允许一个 `com.badlogic.gdx.Game` 存活 |

ADR-001 已锁定整体技术栈；本文件细化"渲染层 vs UI 层"的所有边界、生命周期、状态流向、性能与测试规范，是后续 `:game` 与所有 `:feature:*` 模块的实施红线。**当本文档与任何子模块代码冲突时，以本文档为准**，子模块需提 issue 提议修订。

## 2. 两层职责对比

### 2.1 模块归属总表

| feature 模块 | 是否嵌入 LibGDX | UI 主体 | 备注 |
| --- | --- | --- | --- |
| `:feature:hub` | 是 | Compose HUD + LibGDX 中心区 | 5 图世界的中央枢纽 |
| `:feature:fields` | 是 | Compose 顶栏 + LibGDX 农场视图 | KC 作物可视化 |
| `:feature:mine` | 是 | Compose 任务栏 + LibGDX 矿洞地图 | Bug 矿洞探索 |
| `:feature:profile` | 否 | 纯 Compose | 8 维画像访谈 |
| `:feature:workshop` | 否 | 纯 Compose | 6 类资源生成结果展示 |
| `:feature:arena` | 否 | 纯 Compose | 代码编辑器 + 判题结果 |
| `:feature:retro` | 否 | 纯 Compose | 复盘大厅图表 |
| `:feature:path` | 否 | 纯 Compose | 公告板 + 每日任务 |
| `:feature:classroom` | 否 | 纯 Compose | 班级 KC 热力图 |
| `:feature:dialog` | 否（被 hub/fields/mine 复用） | 纯 Compose 全屏覆盖 | 对话期 LibGDX 强制暂停 |
| `:feature:auth` | 否 | 纯 Compose | 登录 + 生物识别 |

### 2.2 职责边界硬规则

| 维度 | LibGDX 层 | Compose 层 | 红线 |
| --- | --- | --- | --- |
| 渲染对象 | tilemap、sprite、粒子、玩家、NPC、动画 | HUD、对话气泡、任务栏、设置、登录 | LibGDX 严禁绘制文字按钮；Compose 严禁直接调用 `SpriteBatch` |
| 输入 | `InputProcessor` / 触摸 / 虚拟摇杆 / 手柄 | Compose 手势 / Material 控件 | 同一像素区域同一时刻只能由一方处理 |
| 业务逻辑 | 玩家位移、碰撞、NPC AI、粒子寿命 | API 调用、表单校验、JWT、Navigation | LibGDX 不允许直接调 Retrofit；Compose 不允许直接读 `Gdx.input` |
| 状态来源 | `bridge` 内部线程安全字段 | `ViewModel + StateFlow` | 跨层共享态全部经 bridge，禁止静态全局 |
| 资产读取 | `Gdx.files.internal("pixel/maps/*.tmx")` | `painterResource(R.drawable.*)` | tmx / spritesheet 只属于 LibGDX；Material 图标只属于 Compose |
| 线程模型 | GL 渲染线程 | 主线程（Compose），后台 IO 走 Dispatchers.IO | 跨线程必须经 `SharedFlow` / `StateFlow` / `withContext(Main)` |

> 当 hub/fields/mine 之外的 8 个 feature 模块意外需要 LibGDX 时（如 retro 想播放像素回放），**正确做法**是把回放渲染封装为 `:game` 子组件，由该 feature 模块以同样的 bridge 模式嵌入，而非直接 import LibGDX 类。

## 3. 桥接方案

### 3.1 双层架构总图

```text
+------------------------------------------------------------------+
|                       MainActivity (FragmentActivity)            |
|  +------------------- Compose Root (setContent) ---------------+ |
|  |  NavHost                                                    | |
|  |   └─ HubScreen (composable)                                | |
|  |       ├─ HudOverlay   (Material 3)                         | |
|  |       ├─ AndroidView( factory = { FragmentContainerView }) | |
|  |       │     └─ HubLibGdxFragment (AndroidFragmentApplication)
|  |       │            └─ HubGame (com.badlogic.gdx.Game)      | |
|  |       │                  └─ HubScene (ScreenAdapter)       | |
|  |       └─ DialogPanel  (Material 3, 全屏覆盖)                | |
|  +------------------------------------------------------------+ |
|                                                                  |
|  HubViewModel                                                    |
|     ├─ StateFlow<HubUiState>     ──► Compose 收集               |
|     └─ HubGameBridge             ◄─► LibGDX 渲染线程            |
|            ├─ events: MutableSharedFlow<HubEvent>              |
|            ├─ playerTile: StateFlow<TileCoord>                 |
|            └─ interactRequest: AtomicBoolean                   |
+------------------------------------------------------------------+
```

### 3.2 嵌入方式：AndroidView + FragmentContainer

LibGDX 1.13.1 的 `AndroidFragmentApplication` 已基于 `androidx.fragment.app.Fragment`，因此 **必须** 用 `FragmentActivity + supportFragmentManager` 挂载，不能直接 `setContentView(GLSurfaceView)`：

```kotlin
@Composable
fun HubLibGdxView(
    bridge: HubGameBridge,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tag = remember { "hub-libgdx" }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val activity = ctx as FragmentActivity
            FrameLayout(ctx).apply { id = R.id.hub_libgdx_container }.also { container ->
                val fm = activity.supportFragmentManager
                if (fm.findFragmentByTag(tag) == null) {
                    fm.beginTransaction()
                        .add(container.id, HubLibGdxFragment.newInstance(bridge), tag)
                        .commitAllowingStateLoss()
                }
            }
        },
    )
    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? FragmentActivity ?: return@onDispose
            activity.supportFragmentManager.findFragmentByTag(tag)?.let { f ->
                activity.supportFragmentManager.beginTransaction().remove(f).commitAllowingStateLoss()
            }
        }
    }
}
```

### 3.3 LibGDX → ViewModel：SharedFlow

渲染线程是 GL 线程，**禁止** 直接 `viewModel.update(...)`。所有事件经 `MutableSharedFlow` 投递：

```kotlin
class HubGameBridge {
    private val _events = MutableSharedFlow<HubEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<HubEvent> = _events.asSharedFlow()

    fun emit(event: HubEvent) {
        _events.tryEmit(event)
    }
}

class HubViewModel @Inject constructor(
    val bridge: HubGameBridge,
    private val repository: HubRepository,
) : ViewModel() {
    init {
        viewModelScope.launch {
            bridge.events.collect { event ->
                when (event) {
                    is HubEvent.NpcInRange  -> updateNearbyNpc(event.npcKey)
                    HubEvent.NpcOutOfRange -> updateNearbyNpc(null)
                    is HubEvent.ExitTouched -> requestMapSwitch(event.targetMap)
                }
            }
        }
    }
}
```

> 当前 demo 仍使用 `ConcurrentLinkedQueue + 200ms 轮询`（见 `HubGameBridge.poll()`），属于 R-12 临时实现；**正式实现以本节 SharedFlow 方案为准**，迁移点位记在 §13 路线图。

### 3.4 ViewModel → LibGDX：StateFlow + 渲染线程拉取

Compose 改了画质 / 主角能量 / 时间倍率，需反向通知渲染层。用 `StateFlow` 而非回调，**因为渲染线程不能阻塞**：

```kotlin
class HubGameBridge {
    private val _runtimeState = MutableStateFlow(GameRuntimeState())
    val runtimeState: StateFlow<GameRuntimeState> = _runtimeState.asStateFlow()

    fun submitRuntimeState(next: GameRuntimeState) { _runtimeState.value = next }
}

class HubScene(private val bridge: HubGameBridge) : ScreenAdapter() {
    override fun render(delta: Float) {
        val runtime = bridge.runtimeState.value
        cameraZoom = runtime.zoom
        if (runtime.paused) return
        update(delta)
        draw()
    }
}

data class GameRuntimeState(
    val paused: Boolean = false,
    val zoom: Float = 1f,
    val timeScale: Float = 1f,
)
```

## 4. Lifecycle 管理

### 4.1 五段对齐表

| 阶段 | Activity / Compose | LibGDX | 必做动作 |
| --- | --- | --- | --- |
| 创建 | `Activity.onCreate` → `setContent` → `HubScreen` 首帧 | `Fragment.onCreateView` 调 `initializeForView` | 注入 bridge、加载 tmx |
| 显示 | `Activity.onStart/onResume` | `AndroidApplication.onResume` 自动恢复 GL | 重启音效、解暂停 |
| 暂停 | `Activity.onPause`（包括弹窗、来电、Home 键） | `AndroidApplication.onPause` 自动暂停 GL | bridge 标记 `paused=true`，存档自动 flush |
| 后台 | `Activity.onStop` | `Gdx.app.postRunnable` 队列冻结 | 释放音效；保留纹理 |
| 销毁 | `Activity.onDestroy` / `Compose.onDispose` | `Fragment.onDestroyView` → `dispose()` | 显式释放 `SpriteBatch / Texture`，bridge 置空 |

### 4.2 单 Game 实例约束

| 场景 | 处理 |
| --- | --- |
| Compose 重组（recomposition） | `AndroidView` 仅在 key 变更时重建，`HubGame` 经 `gameRef` 缓存复用 |
| Navigation 切到其它 feature | LibGDX Fragment `remove`，`HubGame.dispose()` 释放 GL 资源，下次回到 hub 重新 `initializeForView` |
| 屏幕旋转 | 见 §11.1：必须先把 `bridge.runtimeState`/`playerTile` 备份到 ViewModel，禁用 `configChanges="orientation"` 等绕过手段 |
| 进程被杀 | bridge 不持久；玩家位置由后端 `/api/world/bootstrap` 在下次启动时重灌 |

### 4.3 Fragment.Callbacks 必备

宿主 Activity **必须** 实现 `AndroidFragmentApplication.Callbacks` 并在 `exit()` 调 `finish()`（见 `MainActivity.kt`）。漏实现会触发 `ClassCastException`，所有 LibGDX 集成 PR 必须在 CI 加 lint 校验。

## 5. 状态共享

### 5.1 跨层 vs 内部状态分类

| 状态 | 归属 | 类型 | 跨层方式 |
| --- | --- | --- | --- |
| 玩家 tile 坐标 | 跨层 | `StateFlow<TileCoord>` | LibGDX 写、Compose HUD 读 |
| 玩家能量 / 金币 / 体力 | 跨层 | `StateFlow<PlayerStats>` | 服务端权威，Compose 写 bridge，LibGDX 读 |
| 季节 / 天气 / 时段 | 跨层 | `StateFlow<CalendarToday>` | Compose 拉 `/api/world/today`，bridge 转发 |
| 当前对话 NPC key | 跨层 | `SharedFlow<HubEvent>` | LibGDX 事件 → Compose 弹对话 |
| Tilemap 像素缓存 | LibGDX 内部 | `OrthoCachedTiledMapRenderer` 私有 | **严禁** 放进 StateFlow |
| SpriteBatch / Texture / ShapeRenderer | LibGDX 内部 | 持有引用 | 严禁跨线程持有 |
| Compose 重组键 / NavBackStack | Compose 内部 | `rememberSaveable` | 严禁通过 bridge 暴露给 LibGDX |

### 5.2 共享态命名约定

- bridge 暴露字段 **必须** 用 `latestXxx()` 或 `xxxFlow` 命名，避免与 Compose `state.xxx` 直接重名。
- 严禁 LibGDX 写 ViewModel 的 `MutableStateFlow`；只能 emit 到 bridge 的 `SharedFlow`。
- 严禁 Compose 直接持有 `com.badlogic.gdx.*` 类型字段（编译期阻断，靠 `:feature:*` 不依赖 `:game` 之外的 LibGDX 模块）。

## 6. 资产复用

### 6.1 路径与同源约束

| 类别 | Android 路径 | Web 端同源 | 同步策略 |
| --- | --- | --- | --- |
| Tiled 地图 | `clients/native/android/game/src/main/assets/pixel/maps/*.tmx` | `frontend/public/assets/maps/*.json` | 同源 tmx，用 `scripts/generate-maps.mjs` 双输出 tmx + json |
| SpriteSheet PNG | `clients/native/android/game/src/main/assets/pixel/sprites/*.png` | `frontend/public/assets/pixel/*.png` | 二进制一致；CI 校验 sha256 |
| TexturePack atlas | `clients/native/android/game/src/main/assets/pixel/atlas/*.atlas` | 无（Phaser 自带 spritesheet 拆分） | Android 独有，离线打包工具 `gdx-texture-packer` |
| NPC 元数据 | `clients/native/android/game/src/main/assets/data/npcs/<key>/{meta,dialog}.json` | `frontend/public/data/npcs/...` | Gradle predev 同步 |
| 字体 | `clients/native/android/app/src/main/assets/fonts/*.ttf` | `frontend/src/styles/fonts/` | 三字体均 OFL，二进制一致 |

> Android 资产位于 `:game/src/main/assets/`，由 LibGDX 通过 `Gdx.files.internal(...)` 读取；`:app/src/main/assets/` 仅放 Compose 用的字体 / 图标，避免双层混读。

### 6.2 LICENSE 同步

任何新增 / 替换素材，**必须** 同时更新：

1. `/home/cypress/Pythontopia/LICENSE-ART.md`
2. `/home/cypress/Pythontopia/frontend/public/assets/pixel/LICENSE.md`
3. `/home/cypress/Pythontopia/clients/native/android/game/src/main/assets/pixel/LICENSE.md`（新增，本文件落地后追加）

LPC CC-BY-SA 3.0 传染性已接受：Android APK 二进制以 CC-BY-SA 3.0+ 发布，代码层保持 MIT。

## 7. 输入处理

### 7.1 输入路由表

| 输入源 | 处理层 | 备注 |
| --- | --- | --- |
| 键盘 WASD / 方向键（外接） | LibGDX `Gdx.input.isKeyPressed` | 用于 Chromebook、外接键盘 |
| 触摸方向（屏内虚拟摇杆 / D-Pad） | LibGDX `Touchpad` actor 或自绘 | 单指拖拽，dead-zone 8% |
| 触摸点击空白区域 | LibGDX `InputProcessor.touchDown` | 用于"走到这"或确认 |
| HUD 按钮（背包、设置、对话确认） | Compose `Button` | 完全交给 Compose 手势 |
| 返回键 | Compose `BackHandler` 优先 | 若对话开着则关对话，否则退出当前 feature |
| 系统手势（边缘滑、状态栏） | Compose / 系统 | LibGDX 不拦截 |

### 7.2 重叠区域规则

LibGDX 渲染区与 Compose HUD 在屏幕坐标可能重叠（如顶栏的能量条悬浮在 LibGDX 渲染区之上）。规则：

1. Compose 按钮的可点击区域 **必须** 物理覆盖 LibGDX View；Compose 默认 hit-test 优先 → 自动正确。
2. 严禁让 LibGDX `InputProcessor.touchDown` 与 Compose 同一坐标双方都响应。如必须，Compose 端在 `Modifier.pointerInput` 里调 `consumeAllChanges()`。
3. 虚拟摇杆 **必须** 由 LibGDX 自绘（用 `Stage + Touchpad`），不要用 Compose 模拟摇杆再灌回 bridge，否则有 1-3 帧延迟。

### 7.3 8 方向降级（与 Web 对齐）

Android 端原生支持 8 方向输入：

```kotlin
enum class ExtendedDirection { N, NE, E, SE, S, SW, W, NW, IDLE }

fun toServerDirection(d: ExtendedDirection): String = when (d) {
    ExtendedDirection.N, ExtendedDirection.NE, ExtendedDirection.NW -> "north"
    ExtendedDirection.E, ExtendedDirection.SE -> "east"
    ExtendedDirection.S -> "south"
    ExtendedDirection.W, ExtendedDirection.SW -> "west"
    ExtendedDirection.IDLE -> "idle"
}
```

写后端时降为 4 向（与 Web 一致），渲染保留 8 向用于动画选帧。

## 8. 性能预算

### 8.1 单帧预算（60 FPS = 16.6ms）

| 层 | 预算 | 监控点 |
| --- | --- | --- |
| LibGDX render | ≤ 16ms | `Gdx.graphics.framesPerSecond` ≥ 50；地图切换瞬间允许 1 帧 32ms |
| Compose 重组 | ≤ 4ms | Macrobenchmark `frameDurationCpuMs` p99 |
| bridge 跨线程 | ≤ 1ms | SharedFlow `tryEmit` 同步路径，避免 `emit` 挂起 |
| 总和余量 | ≥ 2ms | 留给 GC / OS 调度 |

### 8.2 内存预算

| 项 | 预算 | 备注 |
| --- | --- | --- |
| LibGDX Texture / Atlas | ≤ 64 MB | TmxMapLoader 启用 mipmap; 5 张地图分别加载，切图释放上一张 |
| Compose Bitmap | ≤ 32 MB | 用 Coil + `MemoryCache(0.25)`；NPC 立绘按需加载 |
| 应用堆总和 | ≤ 256 MB（minSdk 26 起 LargeHeap 关闭） | LeakCanary CI 巡检 |
| APK 增量（LibGDX） | ≤ 8 MB | `abiFilters arm64-v8a` 仅打 arm64 减半 |

### 8.3 帧率红线

- 5 张地图任一切换：FPS 平均 ≥ 50（Pixel 5 基线机）
- 同屏 ≤ 20 NPC + 玩家：FPS 平均 ≥ 55
- 同屏 ≥ 50 粒子：FPS 平均 ≥ 45（粒子寿命 ≤ 1.5s）
- Compose HUD 单次重组：< 4ms（不含 LibGDX 帧）

CI 用 `Macrobenchmark` 抓 `StartupTimingMetric + FrameTimingMetric`，超阈值阻断合入。

## 9. 测试策略

### 9.1 测试金字塔

| 类型 | 工具 | 覆盖目标 |
| --- | --- | --- |
| LibGDX 纯逻辑 | JUnit 5 + `HeadlessApplication`（`gdx-backend-headless`） | `HubScene.checkProximity`、`touchToDirection`、碰撞逻辑 |
| LibGDX 渲染回归 | Roborazzi + headless GL（Mesa） | tilemap 首帧像素 hash 一致 |
| bridge 并发 | JUnit + Turbine | SharedFlow / StateFlow 顺序、丢帧、背压 |
| ViewModel | JUnit + Turbine + MockK | 收到 `NpcInRange` → state 变化 |
| Compose UI | Compose UI Test + Robolectric | HUD 按钮可点、对话面板显示 |
| 集成 | Espresso + AndroidJUnitRunner（真机 / 模拟器） | hub→ dialog → 返回 hub LibGDX 仍渲染 |
| 性能 | Macrobenchmark + Perfetto | FPS、内存、启动耗时 |
| 截图 | Roborazzi（包含 hub LibGDX 渲染区） | 视觉回归 |

### 9.2 Headless LibGDX 模板

```kotlin
class HubSceneProximityTest {
    private lateinit var app: HeadlessApplication
    private lateinit var bridge: HubGameBridge

    @BeforeEach
    fun setUp() {
        val config = HeadlessApplicationConfiguration().apply { updatesPerSecond = -1 }
        bridge = HubGameBridge()
        app = HeadlessApplication(HubGame(bridge), config)
    }

    @AfterEach
    fun tearDown() { app.exit() }

    @Test
    fun `walking next to mayor_py emits NpcInRange`() = runTest {
        bridge.events.test {
            simulateMoveTo(tileX = 25, tileY = 11)
            val first = awaitItem()
            assertThat(first).isInstanceOf(HubEvent.NpcInRange::class.java)
        }
    }
}
```

> headless 跑不动 OpenGL 真实绘制，但 `ShapeRenderer.begin/end` 在 headless 后端是 no-op，逻辑测试不受影响。

## 10. 代码示例

### 10.1 Hub 三段式布局

```kotlin
@Composable
fun HubScreen(
    onLogout: () -> Unit,
    hubViewModel: HubViewModel = hiltViewModel(),
    dialogViewModel: DialogViewModel = hiltViewModel(),
) {
    val state by hubViewModel.state.collectAsStateWithLifecycle()
    val dialogState by dialogViewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            HubTopHud(state = state, modifier = Modifier.fillMaxWidth().height(72.dp))
            HubLibGdxView(
                bridge = hubViewModel.bridge,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            HubBottomQuestBar(
                state = state,
                onInteract = hubViewModel::requestInteract,
                modifier = Modifier.fillMaxWidth().height(96.dp),
            )
        }
        if (dialogState.visible) {
            NpcDialogPanel(
                viewModel = dialogViewModel,
                onDismissed = { hubViewModel.dialogDismissed() },
            )
        }
    }
}
```

### 10.2 Dialog 期 LibGDX 暂停

```kotlin
@Composable
fun NpcDialogPanel(
    viewModel: DialogViewModel,
    onDismissed: () -> Unit,
) {
    val hubViewModel: HubViewModel = hiltViewModel()
    DisposableEffect(Unit) {
        hubViewModel.bridge.submitRuntimeState(
            hubViewModel.bridge.runtimeState.value.copy(paused = true),
        )
        onDispose {
            hubViewModel.bridge.submitRuntimeState(
                hubViewModel.bridge.runtimeState.value.copy(paused = false),
            )
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        DialogContent(viewModel = viewModel, onDismissed = onDismissed)
    }
}
```

### 10.3 跨线程切回主线程

```kotlin
suspend fun saveProgress(playerTile: TileCoord) {
    withContext(Dispatchers.IO) { repository.savePlayer(playerTile) }
    withContext(Dispatchers.Main) { hubViewModel.markSaved(playerTile) }
}
```

## 11. 常见陷阱

### 11.1 屏幕旋转：LibGDX disposed 必须保存 state

`AndroidFragmentApplication` 在屏幕旋转时会 `dispose()` 整个 GL 上下文。**必须**：

1. ViewModel `viewModelScope` 不随 Activity 销毁，bridge 持有的 `playerTile`、`runtimeState` 自动保留。
2. 严禁通过 `configChanges="orientation|screenSize"` 绕过销毁；这会破坏 Compose recomposition + 资源回收。
3. `HubLibGdxFragment.gameRef` 缓存只在 Fragment 实例内有效；新 Fragment 创建后用 bridge 内字段重建场景。

### 11.2 EventBus 切回主线程

LibGDX 渲染线程发出的事件，进入 `MutableSharedFlow` 后由 ViewModel `viewModelScope.launch{}` 收集——`viewModelScope` 默认 `Dispatchers.Main.immediate`，因此 `_state.update { ... }` 已在主线程，**不要** 再嵌一层 `withContext(Main)`，否则会增加 1 帧延迟。但 **如果** 后续要操作 Compose 直接 API（罕见），必须显式 `withContext(Dispatchers.Main)`。

### 11.3 SpriteBatch 不可跨线程

`SpriteBatch / ShapeRenderer / Texture` 持有 OpenGL handle，**只能** 在 LibGDX 渲染线程使用。常见错误：

```kotlin
// 错误：在 Compose ViewModel 里持有 SpriteBatch
class WrongViewModel : ViewModel() {
    private val batch = SpriteBatch() // CRASH on render
}
```

正确做法：所有 GL 资源都封装在 `Screen` / `Disposable` 里，由 `HubGame` 统一管理生命周期。

### 11.4 多 Game 实例：触发 GLSurfaceView 抢上下文

`HubGame` 与未来的 `MineGame` **必须** 在不同时刻存活，绝不允许同时 `AndroidView` 创建两个 `AndroidFragmentApplication`。Navigation 在 hub→mine 切换时先 `remove` hub fragment 再 `add` mine fragment，靠 `DisposableEffect.onDispose` 保证。

### 11.5 SharedFlow 丢帧

`MutableSharedFlow(extraBufferCapacity = 0)` 在订阅者来不及消费时会丢事件。**必须** 设置 `extraBufferCapacity = 64`，且事件类型设计成幂等（如 `NpcInRange` 重发不会引发副作用）。

### 11.6 Compose AndroidView factory 重入

`AndroidView` 的 `factory` 在重组时可能被多次调用。必须用 `remember` 或 `findFragmentByTag` 防重复挂载，否则会有多个 `HubLibGdxFragment` 叠加渲染。

### 11.7 R-12 demo 轮询桥的迁移点

当前 `HubGameBridge.poll()` + 200ms 轮询是 R-12 临时实现；在 R-13 替换为 `SharedFlow.collect` 时需同步：

1. 移除 `HubScreen` 内 `while (true) { bridge.poll(); delay(200) }` 死循环
2. ViewModel `init { viewModelScope.launch { bridge.events.collect { ... } } }`
3. headless 测试改用 Turbine 的 `bridge.events.test { ... }`

## 12. 替代方案讨论

| 备选 | 评分 | 否决理由 |
| --- | --- | --- |
| **Compose Canvas 全包**（不引入 LibGDX） | 5/10 | DrawScope 在 50×40 tile 同屏渲染时帧率掉到 30 以下；Material 3 ripple/动效与像素风冲突；缺少 tmx 直读、缺少粒子系统、缺少音频混音 |
| **KorGE 4.x** | 6/10 | Kotlin Multiplatform 原生友好，但 Android 后端稳定性不如 LibGDX，社区量级小（GitHub star LibGDX 23k vs KorGE 2k），文档薄；与 Web Phaser 范式差距大，学习曲线陡 |
| **Cocos2d-x（Cocos Creator 3.x）** | 5/10 | C++ + JS bridge 双层增加运维成本；Android 集成需 ndk-build，CI 复杂度高；Material 3 集成需自写 native binding |
| **Unity** | 4/10 | APK 增量 ≥ 25 MB；Asset Store 商业模型与 OFL/CC-BY-SA 不兼容；Compose 嵌入需 `UnityPlayerActivity` 替换根 Activity，与 Navigation 冲突 |
| **LibGDX + Compose（本方案）** | 9/10 | 评分 36/40（A2 调研），APK 增量 4-6 MB；社区活跃；TmxMapLoader 与 Web Phaser tilemap 等价；headless 测试友好 |

> 决策出处见 `clients/native/android/docs/ADR-001-tech-selection.md` §1 行 16；本文件 §1 已对齐结论。

## 13. 验收清单

### 13.1 R-12 必达项

| 项 | 要求 | 验证方式 |
| --- | --- | --- |
| 5 张地图切换不卡顿 | hub ↔ farm_north / mine_south / campus_east / port_west：切换 FPS ≥ 50（Pixel 5 基线） | Macrobenchmark `FrameTimingMetric` p95 ≤ 20ms |
| Compose HUD 不抢 LibGDX 输入 | HUD 按钮点击响应；同时 LibGDX 触摸方向正常 | Espresso 集成测试覆盖 hub `tap HUD button → click empty area to walk` |
| 屏幕旋转保留状态 | 旋转后玩家 tile 坐标不变 | UI Test `setDeviceOrientation` 前后 `bridge.latestPlayerTile()` 一致 |
| 单 Game 实例 | 任意时刻 `Application.gameRef` 全局只一个 | LeakCanary 监控；CI lint 规则禁止在多个 feature 模块同时 `new HubGame()` |
| 对话期暂停 | NpcDialogPanel 显示时 `bridge.runtimeState.paused == true` | Compose UI Test + bridge spy |
| 资产同源 | `town_hub.tmx` sha256 与 `town_hub.json` 反向校验一致 | CI `scripts/verify-assets.sh` |
| 性能预算 | LibGDX ≤ 16ms / Compose ≤ 4ms / Texture ≤ 64 MB / Compose Bitmap ≤ 32 MB | Macrobenchmark + Perfetto |
| 测试覆盖 | `:game` ≥ 70% 行覆盖；bridge ≥ 90% | Kover HTML 报告 |

### 13.2 R-13 演进项（提示，非本期验收）

- bridge 从 `ConcurrentLinkedQueue + 轮询` 迁移到 `MutableSharedFlow + collect`
- `ShapeRenderer` 占位渲染替换为 `SpriteBatch + TextureAtlas`
- `TmxMapLoader` 接入 5 张地图原始 tmx
- 8 方向角色动画接入（`AnimatedTiledMapTile` + 4 行 sprite sheet）
- 虚拟摇杆 `Touchpad` actor 上线（替换当前 D-Pad 触摸方向算法）

## 14. 附录：当前实现与本规范的差距清单

| 当前文件 | 现状 | 与本规范差距 | 处理 |
| --- | --- | --- | --- |
| `HubGameBridge` | `ConcurrentLinkedQueue.poll` + `AtomicBoolean` | 缺 SharedFlow / StateFlow | R-13 改写，本期保留 |
| `HubScene` | `ShapeRenderer` 程序化绘制 | 无 TmxMapLoader、无 SpriteBatch | R-13 替换 |
| `HubScreen.LaunchedEffect { while(true) poll() }` | 死循环轮询 | 应改 `bridge.events.collect` | 与 bridge 同步迁移 |
| `assets/pixel/maps/*.tmx` | 尚未提交 | 需 `scripts/generate-maps.mjs` 双输出 | R-12 末入库 |
| `:feature:fields`、`:feature:mine` | 仅占位 Compose | 需嵌入 LibGDX | R-12 demo 暂不要求 |
| Macrobenchmark / Roborazzi | 未启用 | 需要在 CI 加 lane | R-12 末上线 |

> 本文档的"硬规则"立即生效；"演进项"按 R-12 → R-13 时间线推进。任何偏离硬规则的实现必须先改本文档并经评审，再写代码。
