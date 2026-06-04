package com.pythontopia.app

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PythontopiaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }
    }

    /**
     * 仅 debug 启用：把主线程磁盘/网络 I/O、泄漏的 Closable / SharedPreferences 等
     * 当作可观测违规打到 logcat（penaltyLog），便于在开发期抓出会卡 UI 的隐患
     * （如 EncryptedSharedPreferences 误在主线程访问）。release 不启用，零开销。
     */
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
