package com.pythontopia.feature.hub

import android.util.Log
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.pythontopia.game.HubGameBridge
import com.pythontopia.game.HubLibGdxFragment

/**
 * Compose 端 LibGDX 嵌入点：把 [HubLibGdxFragment] 挂在 [FragmentActivity.supportFragmentManager]
 * 上，靠 [AndroidView] + [FrameLayout] 容器承载。文档 §3.2 给出标准范式，本类是它的可复用实现。
 *
 * 与既有 [HubScreen] 的内联 `HubLibGdxView` 私有函数差异：
 * - 抽出为顶层 public composable，便于 R-13 在 `:feature:fields` / `:feature:mine` 复用；
 * - 引入 [LibGdxMountRegistry]（Application-process 级单例）守护"单 Game 实例"约束：
 *   同一时刻只允许一个 bridge 挂载，若发现重复挂载（多 feature 同时入栈）会 fail-fast
 *   抛 [IllegalStateException]，避免两个 `AndroidApplication` 抢 GL 上下文导致黑屏 / 崩溃；
 * - 不再持有 Compose / ViewModel 引用，构造仅靠 [HubGameBridge]，方便 Preview / 截图测试。
 *
 * 进入退出语义：
 * - 首次进入：在 `Application` 级注册 mount → 创建 Fragment → `initializeForView` 起 GL；
 * - 重组：`AndroidView` 工厂函数可能被多次调用，依靠 FragmentManager 的 tag 查找避免重复 add；
 * - 退出：`DisposableEffect.onDispose` 移除 Fragment + 取消注册。
 */
@Composable
fun HubLibGdxView(
    bridge: HubGameBridge,
    modifier: Modifier = Modifier,
    fragmentTag: String = DEFAULT_FRAGMENT_TAG,
) {
    val context = LocalContext.current
    val mountKey = remember(bridge, fragmentTag) { MountKey(System.identityHashCode(bridge), fragmentTag) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val activity = ctx as? FragmentActivity
                ?: throw IllegalStateException(
                    "HubLibGdxView host must be FragmentActivity (MainActivity already is). " +
                        "Direct Activity / wrapped ContextWrapper without FragmentActivity will crash AndroidFragmentApplication.",
                )
            LibGdxMountRegistry.register(mountKey)
            FrameLayout(ctx).apply { id = R.id.hub_libgdx_container }.also { container ->
                val fm = activity.supportFragmentManager
                if (fm.findFragmentByTag(fragmentTag) == null) {
                    val fragment = HubLibGdxFragment.newInstance(bridge)
                    fm.beginTransaction()
                        .add(container.id, fragment, fragmentTag)
                        .commitAllowingStateLoss()
                }
            }
        },
    )

    DisposableEffect(mountKey) {
        onDispose {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val fm = activity.supportFragmentManager
                fm.findFragmentByTag(fragmentTag)?.let { fragment ->
                    fm.beginTransaction()
                        .remove(fragment)
                        .commitAllowingStateLoss()
                }
            }
            LibGdxMountRegistry.unregister(mountKey)
        }
    }
}

/** 装载点的稳定 key；用 bridge 的 identityHashCode 而非 hashCode，避免被 data class 误覆盖。 */
internal data class MountKey(val bridgeId: Int, val tag: String)

/**
 * Application 级 LibGDX 装载注册表：进程内必须保证最多 1 个 [HubLibGdxFragment] 同时存活，
 * 否则两个 `AndroidApplication` 会抢 GL 上下文（文档 §11.4）。
 *
 * 这里用 `@Volatile` + `synchronized` 自包装的"轻量 Application 级单例"实现，避免引入
 * `ApplicationStartup` 等基础设施依赖；JVM 类加载器为整个进程提供唯一实例，等价于"Application 级"。
 */
internal object LibGdxMountRegistry {
    @Volatile
    private var current: MountKey? = null

    @Synchronized
    fun register(key: MountKey) {
        val existing = current
        if (existing != null && existing != key) {
            Log.w(
                "HubLibGdxView",
                "LibGDX already mounted with key=$existing; refusing duplicate mount for $key. " +
                    "Check Navigation: hub/fields/mine must not be on stack simultaneously.",
            )
            throw IllegalStateException(
                "HubLibGdxView mount conflict: existing=$existing, incoming=$key. " +
                    "Single Game instance constraint violated (docs/android/03 §4.2).",
            )
        }
        current = key
    }

    @Synchronized
    fun unregister(key: MountKey) {
        if (current == key) {
            current = null
        }
    }

    @Synchronized
    fun currentForTest(): MountKey? = current
}

private const val DEFAULT_FRAGMENT_TAG: String = "hub-libgdx"
