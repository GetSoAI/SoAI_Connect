// SPDX-License-Identifier: MIT

package com.soai.android.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.os.Build
import androidx.core.content.edit
import com.soai.android.R
import com.soai.android.network.SessionCertificateTrust
import com.soai.android.notifications.SoAINotificationSeenStorage
import org.json.JSONArray
import java.util.UUID
import com.soai.android.web.AndroidDeviceIdentity

class AppPreferences private constructor(context: Context) : SoAINotificationSeenStorage {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) = prefs.edit { putString(KEY_SERVER_URL, value) }

    var incognitoMode: Boolean
        get() = prefs.getBoolean(KEY_INCOGNITO_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_INCOGNITO_MODE, value) }

    var serverInstanceId: String?
        get() = prefs.getString(KEY_SERVER_INSTANCE_ID, null)
        set(value) = prefs.edit { putString(KEY_SERVER_INSTANCE_ID, value) }

    var trustedServerOrigin: String?
        get() = prefs.getString(KEY_TRUSTED_SERVER_ORIGIN, null)
        set(value) = prefs.edit { putString(KEY_TRUSTED_SERVER_ORIGIN, value) }

    var trustedServerInstanceId: String?
        get() = prefs.getString(KEY_TRUSTED_SERVER_INSTANCE_ID, null)
        set(value) = prefs.edit { putString(KEY_TRUSTED_SERVER_INSTANCE_ID, value) }

    var trustedServerCertSha256: String?
        get() = prefs.getString(KEY_TRUSTED_SERVER_CERT_SHA256, null)
        set(value) = prefs.edit { putString(KEY_TRUSTED_SERVER_CERT_SHA256, value) }

    var wolMac: String?
        get() = prefs.getString(KEY_WOL_MAC, null)
        set(value) = prefs.edit { putString(KEY_WOL_MAC, value) }

    var wolBroadcastAddress: String?
        get() = prefs.getString(KEY_WOL_BROADCAST_ADDRESS, null)
        set(value) = prefs.edit { putString(KEY_WOL_BROADCAST_ADDRESS, value) }

    var wolPort: Int
        get() = prefs.getInt(KEY_WOL_PORT, DEFAULT_WOL_PORT)
        set(value) = prefs.edit { putInt(KEY_WOL_PORT, value) }

    var androidNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANDROID_NOTIFICATIONS_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ANDROID_NOTIFICATIONS_ENABLED, value) }

    var webCachePopulated: Boolean
        get() = prefs.getBoolean(KEY_WEB_CACHE_POPULATED, false)
        set(value) = prefs.edit { putBoolean(KEY_WEB_CACHE_POPULATED, value) }

    val deviceId: String
        get() {
            val stored = prefs.getString(KEY_DEVICE_ID, null)
            if (stored != null) {
                val valid = try {
                    UUID.fromString(stored).toString() == stored.lowercase()
                } catch (exception: IllegalArgumentException) {
                    false
                }
                if (valid) return stored.lowercase()
            }
            val generated = UUID.randomUUID().toString()
            prefs.edit { putString(KEY_DEVICE_ID, generated) }
            return generated
        }

    var deviceLabel: String
        get() {
            val stored = prefs.getString(KEY_DEVICE_LABEL, null)
            if (!stored.isNullOrBlank()) {
                val normalized = AndroidDeviceIdentity.normalizeLabel(stored)
                if (normalized.isNotEmpty()) {
                    if (normalized != stored) prefs.edit { putString(KEY_DEVICE_LABEL, normalized) }
                    return normalized
                }
            }
            val generated = AndroidDeviceIdentity.normalizeLabel(Build.MODEL)
                .ifEmpty {
                    AndroidDeviceIdentity.normalizeLabel(
                        appContext.getString(R.string.default_device_label)
                    )
                }
            prefs.edit { putString(KEY_DEVICE_LABEL, generated) }
            return generated
        }
        set(value) {
            val normalized = AndroidDeviceIdentity.normalizeLabel(value)
            require(normalized.isNotEmpty())
            prefs.edit { putString(KEY_DEVICE_LABEL, normalized) }
        }

    fun deviceIdentity(): AndroidDeviceIdentity {
        return AndroidDeviceIdentity(deviceId, deviceLabel)
    }

    override var notificationBaselineServerUrl: String?
        get() = prefs.getString(KEY_NOTIFICATION_BASELINE_SERVER_URL, null)
        set(value) = prefs.edit { putString(KEY_NOTIFICATION_BASELINE_SERVER_URL, value) }

    override var notificationSeenIds: List<String>
        get() = decodeStringList(prefs.getString(KEY_NOTIFICATION_SEEN_IDS, null))
        set(value) = prefs.edit { putString(KEY_NOTIFICATION_SEEN_IDS, encodeStringList(value)) }

    fun clearTrustedServerCertificate() {
        SessionCertificateTrust.clear()
        prefs.edit {
            remove(KEY_TRUSTED_SERVER_ORIGIN)
            remove(KEY_TRUSTED_SERVER_INSTANCE_ID)
            remove(KEY_TRUSTED_SERVER_CERT_SHA256)
        }
    }

    fun saveConnectedServer(serverUrl: String, instanceId: String) {
        prefs.edit {
            putString(KEY_SERVER_URL, serverUrl)
            putString(KEY_SERVER_INSTANCE_ID, instanceId)
        }
    }

    fun hasConnectedServer(): Boolean {
        return !serverUrl.isNullOrBlank() && !serverInstanceId.isNullOrBlank()
    }

    fun saveTrustedServerCertificate(origin: String, instanceId: String, fingerprint: String) {
        prefs.edit {
            putString(KEY_TRUSTED_SERVER_ORIGIN, origin)
            putString(KEY_TRUSTED_SERVER_INSTANCE_ID, instanceId)
            putString(KEY_TRUSTED_SERVER_CERT_SHA256, fingerprint)
        }
    }

    fun clearNativeNotificationState() {
        prefs.edit {
            remove(KEY_NOTIFICATION_BASELINE_SERVER_URL)
            remove(KEY_NOTIFICATION_SEEN_IDS)
        }
    }

    fun clearServerState() {
        SessionCertificateTrust.clear()
        prefs.edit {
            remove(KEY_SERVER_URL)
            remove(KEY_SERVER_INSTANCE_ID)
            remove(KEY_TRUSTED_SERVER_ORIGIN)
            remove(KEY_TRUSTED_SERVER_INSTANCE_ID)
            remove(KEY_TRUSTED_SERVER_CERT_SHA256)
            remove(KEY_NOTIFICATION_BASELINE_SERVER_URL)
            remove(KEY_NOTIFICATION_SEEN_IDS)
            putBoolean(KEY_WEB_CACHE_POPULATED, false)
        }
    }

    private fun encodeStringList(values: List<String>): String {
        val array = JSONArray()
        values.forEach { value -> array.put(value) }
        return array.toString()
    }

    private fun decodeStringList(encoded: String?): List<String> {
        if (encoded.isNullOrBlank()) return emptyList()
        val array = try {
            JSONArray(encoded)
        } catch (exception: Exception) {
            Log.w(TAG, "Discarding malformed notification state", exception)
            prefs.edit { remove(KEY_NOTIFICATION_SEEN_IDS) }
            return emptyList()
        }
        val result = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotBlank()) result.add(value)
        }
        return result
    }

    companion object {
        private const val PREFS_NAME = "soai_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_INCOGNITO_MODE = "incognito_mode"
        private const val KEY_SERVER_INSTANCE_ID = "server_instance_id"
        private const val KEY_TRUSTED_SERVER_ORIGIN = "trusted_server_origin"
        private const val KEY_TRUSTED_SERVER_INSTANCE_ID = "trusted_server_instance_id"
        private const val KEY_TRUSTED_SERVER_CERT_SHA256 = "trusted_server_cert_sha256"
        private const val KEY_WOL_MAC = "wol_mac"
        private const val KEY_WOL_BROADCAST_ADDRESS = "wol_broadcast_address"
        private const val KEY_WOL_PORT = "wol_port"
        private const val KEY_ANDROID_NOTIFICATIONS_ENABLED = "android_notifications_enabled"
        private const val KEY_WEB_CACHE_POPULATED = "web_cache_populated"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_LABEL = "device_label"
        private const val KEY_NOTIFICATION_BASELINE_SERVER_URL = "notification_baseline_server_url"
        private const val KEY_NOTIFICATION_SEEN_IDS = "notification_seen_ids"
        private const val DEFAULT_WOL_PORT = 9

        private const val TAG = "AppPreferences"

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
