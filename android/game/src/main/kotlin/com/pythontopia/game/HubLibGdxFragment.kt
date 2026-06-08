package com.pythontopia.game

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.badlogic.gdx.backends.android.AndroidFragmentApplication

/**
 * Compose 通过 AndroidView(FragmentContainerView) 挂载本 Fragment，
 * 在 [onCreateView] 时由 [AndroidFragmentApplication.initializeForView] 返回 LibGDX 渲染视图。
 *
 * 每个实例必须经 [newInstance] 创建并先调用 [attachBridge] 才会渲染；这样：
 * - LibGDX 渲染线程持有的桥对象由 Compose 层显式注入，避免静态全局。
 * - 多次 onCreateView 不会重复创建 Game（用 [gameRef] 缓存）。
 *
 * 宿主 Activity 必须实现 [AndroidFragmentApplication.Callbacks]（demo 里的 MainActivity 即如此）。
 */
class HubLibGdxFragment : AndroidFragmentApplication() {

    private var bridge: HubGameBridge? = null
    private var gameRef: HubGame? = null

    fun attachBridge(bridge: HubGameBridge) {
        this.bridge = bridge
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val activeBridge = bridge ?: error(
            "HubLibGdxFragment requires attachBridge(...) before onCreateView " +
                "(call HubLibGdxFragment.newInstance(bridge) when adding to fragment manager)",
        )
        val game = gameRef ?: HubGame(activeBridge).also { gameRef = it }
        return initializeForView(game, HubGameLauncher.applicationConfiguration())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 显式 dispose 由 :feature:hub 在 onDispose 时安排。
    }

    companion object {
        fun newInstance(bridge: HubGameBridge): HubLibGdxFragment =
            HubLibGdxFragment().apply { attachBridge(bridge) }
    }
}
