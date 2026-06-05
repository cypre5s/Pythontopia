package com.pythontopia.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.networkConfigDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "pythontopia_network_config")

/**
 * 后端 baseUrl 运行时覆盖值的持久层（DataStore Preferences）。
 *
 * 只存一个可空字符串：测试人员手填的覆盖地址。为空表示"未覆盖"，
 * 上层 [BaseUrlProvider] 据此回退到 [NetworkConfig.DEFAULT_BASE_URL]（即 BuildConfig.BASE_URL）。
 */
class NetworkHostStore(private val context: Context) {

    private val overrideKey = stringPreferencesKey(KEY_BASE_URL_OVERRIDE)

    /** 观察覆盖值变化（供设置 UI），`null` 表示未覆盖。 */
    val overrideFlow: Flow<String?> =
        context.networkConfigDataStore.data.map { prefs -> prefs[overrideKey] }

    suspend fun read(): String? = context.networkConfigDataStore.data.first()[overrideKey]

    suspend fun write(value: String?) {
        context.networkConfigDataStore.edit { prefs ->
            if (value.isNullOrBlank()) {
                prefs.remove(overrideKey)
            } else {
                prefs[overrideKey] = value
            }
        }
    }

    private companion object {
        const val KEY_BASE_URL_OVERRIDE = "base_url_override"
    }
}
