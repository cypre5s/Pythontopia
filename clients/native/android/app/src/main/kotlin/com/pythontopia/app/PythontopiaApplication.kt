package com.pythontopia.app

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale

@HiltAndroidApp
class PythontopiaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 本地化：强制简体中文，确保日期/数字格式与系统级文案不随设备语言漂移。
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
    }

    /** debug 启用 StrictMode，主线程磁盘/网络 I/O 等违规打 logcat；release 不启用。 */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build(),
        )
    }
}
