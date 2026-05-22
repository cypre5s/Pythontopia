package com.pythontopia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.pythontopia.core.design.PythontopiaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 顶层 Activity：
 *  - 继承 [ComponentActivity]，让 `setContent { ... }` 工作；
 *  - LibGDX 1.13.1 的 `AndroidFragmentApplication` 仍走 *平台* `android.app.Fragment`，
 *    所以 [MainActivity] 不需要 `FragmentActivity`，平台 `fragmentManager` 即可用；
 *  - 实现 [AndroidFragmentApplication.Callbacks]：libGDX 内部调用 `Gdx.app.exit()`
 *    时会触发 [exit]，demo 仅退出当前 Activity；
 *  - `@AndroidEntryPoint` 让 Hilt 注入 Compose 嵌套的 ViewModel。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity(), AndroidFragmentApplication.Callbacks {

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
