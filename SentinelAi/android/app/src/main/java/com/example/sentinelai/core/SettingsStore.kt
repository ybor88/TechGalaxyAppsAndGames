package com.example.sentinelai.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sentinelai_settings")

/**
 * App settings, stored in the app's private sandboxed storage (DataStore) —
 * the same trust boundary as any Android app's own data, never shared with
 * other apps or committed anywhere.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val REALTIME_ENABLED = booleanPreferencesKey("realtime_enabled")
        val AUTO_QUARANTINE_THRESHOLD = intPreferencesKey("auto_quarantine_threshold")
        val VT_API_KEY = stringPreferencesKey("vt_api_key")
        val CLOUD_LOOKUP_ENABLED = booleanPreferencesKey("cloud_lookup_enabled")
    }

    val realtimeEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.REALTIME_ENABLED] ?: false }

    val autoQuarantineThreshold: Flow<Int> =
        context.dataStore.data.map { it[Keys.AUTO_QUARANTINE_THRESHOLD] ?: 70 }

    val vtApiKey: Flow<String> =
        context.dataStore.data.map { it[Keys.VT_API_KEY] ?: "" }

    val cloudLookupEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.CLOUD_LOOKUP_ENABLED] ?: false }

    suspend fun setRealtimeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REALTIME_ENABLED] = enabled }
    }

    suspend fun setAutoQuarantineThreshold(value: Int) {
        context.dataStore.edit { it[Keys.AUTO_QUARANTINE_THRESHOLD] = value }
    }

    suspend fun setVtApiKey(key: String) {
        context.dataStore.edit { it[Keys.VT_API_KEY] = key }
    }

    suspend fun setCloudLookupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CLOUD_LOOKUP_ENABLED] = enabled }
    }

    suspend fun isCloudConfigured(): Boolean {
        val key = context.dataStore.data.map { it[Keys.VT_API_KEY] ?: "" }.first()
        val enabled = context.dataStore.data.map { it[Keys.CLOUD_LOOKUP_ENABLED] ?: false }.first()
        return key.isNotBlank() && enabled
    }
}
