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
 * Compose 端 LibGDX 嵌入；宿主须为 [FragmentActivity]。
 * [LibGdxMountRegistry] 保证进程内单 Game 实例，重复挂载 fail-fast；
 * 退出时 [DisposableEffect] 移除 Fragment 并取消注册。
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

/** 装载点 key；用 bridge identityHashCode 而非 hashCode。 */
internal data class MountKey(val bridgeId: Int, val tag: String)

/** 进程级 LibGDX 装载注册表；同时最多 1 个 [HubLibGdxFragment]，防 GL 上下文冲突。 */
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
                    "Single Game instance constraint violated (docs/clients/android/03 §4.2).",
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
