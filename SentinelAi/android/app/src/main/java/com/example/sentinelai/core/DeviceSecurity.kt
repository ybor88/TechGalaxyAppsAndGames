package com.example.sentinelai.core

import android.app.KeyguardManager
import android.content.Context
import android.provider.Settings

/**
 * System security posture checks available to a normal (non-privileged)
 * Android app — the mobile equivalent of the desktop's Firewall/Defender
 * status cards. No special permissions required.
 */
object DeviceSecurity {

    fun screenLockStatus(context: Context): String {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            ?: return "non disponibile"
        return if (keyguardManager.isDeviceSecure) "attivo" else "disattivato"
    }

    fun playProtectStatus(context: Context): String {
        return try {
            val value = Settings.Global.getInt(context.contentResolver, "package_verifier_enable", 1)
            if (value == 1) "attivo" else "disattivato"
        } catch (exc: Settings.SettingNotFoundException) {
            "sconosciuto"
        }
    }
}
