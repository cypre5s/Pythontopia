package com.pythontopia.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.pythontopia.core.design.PythontopiaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 顶层 Activity：
 *  - 继承 [FragmentActivity]（本身就是 `androidx.activity.ComponentActivity` 的子类），
 *    让 `setContent { ... }` 工作；同时提供 [supportFragmentManager]，
 *    因为 LibGDX 1.13.1 的 `AndroidFragmentApplication` 已迁移到 `androidx.fragment.app.Fragment`，
 *    必须通过 androidx FragmentManager 挂载（详见 :feature:hub 的 HubLibGdxView）；
 *  - 实现 [AndroidFragmentApplication.Callbacks]：libGDX 内部调用 `Gdx.app.exit()`
 *    时会触发 [exit]，demo 仅退出当前 Activity；
 *  - `@AndroidEntryPoint` 让 Hilt 注入 Compose 嵌套的 ViewModel。
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity(), AndroidFragmentApplication.Callbacks {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PythontopiaTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    override fun exit() {
        finish()
    }
}
