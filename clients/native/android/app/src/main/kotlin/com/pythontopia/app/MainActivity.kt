package com.pythontopia.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.pythontopia.core.design.PythontopiaTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

/**
 * 顶层 Activity：[FragmentActivity] 供 Compose 与 LibGDX [AndroidFragmentApplication] 共存；
 * [exit] 响应 `Gdx.app.exit()`。
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity(), AndroidFragmentApplication.Callbacks {

    /** 本地化：把 Activity 资源上下文锁定到简体中文，系统级文案/资源不随设备语言变化。 */
    override fun attachBaseContext(newBase: Context) {
        val locale = Locale.SIMPLIFIED_CHINESE
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(locale) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

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
