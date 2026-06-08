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
 * 顶层 Activity：[FragmentActivity] 供 Compose 与 LibGDX [AndroidFragmentApplication] 共存；
 * [exit] 响应 `Gdx.app.exit()`。
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
