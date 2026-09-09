package com.example.sentinelai.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppAuditEntry(
    val appName: String,
    val packageName: String,
    val dangerousPermissions: List<String>,
    val isSystemApp: Boolean
)

/**
 * Privacy & Control on Android: modern Android does not let third-party
 * apps see other apps' running processes (that API was locked down years
 * ago), so the meaningful equivalent is a permission audit — which
 * user-launchable apps hold sensitive permissions like camera, contacts,
 * location or SMS. Only queries launcher-visible apps, not the restricted
 * QUERY_ALL_PACKAGES surface.
 */
object PrivacyAudit {
    private val DANGEROUS_PERMISSIONS = mapOf(
        "android.permission.CAMERA" to "Fotocamera",
        "android.permission.RECORD_AUDIO" to "Microfono",
        "android.permission.READ_CONTACTS" to "Contatti",
        "android.permission.ACCESS_FINE_LOCATION" to "Posizione precisa",
        "android.permission.ACCESS_COARSE_LOCATION" to "Posizione approssimativa",
        "android.permission.READ_SMS" to "SMS",
        "android.permission.SEND_SMS" to "Invio SMS",
        "android.permission.READ_CALL_LOG" to "Registro chiamate",
        "android.permission.READ_EXTERNAL_STORAGE" to "Archiviazione",
        "android.permission.BODY_SENSORS" to "Sensori corporei"
    )

    fun auditInstalledApps(context: Context): List<AppAuditEntry> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchableApps = pm.queryIntentActivities(launcherIntent, 0)

        return launchableApps.mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            try {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                val granted = packageInfo.requestedPermissions?.toList() ?: emptyList()
                val dangerous = granted.mapNotNull { DANGEROUS_PERMISSIONS[it] }
                val appInfo = pm.getApplicationInfo(packageName, 0)
                AppAuditEntry(
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    packageName = packageName,
                    dangerousPermissions = dangerous,
                    isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                )
            } catch (exc: PackageManager.NameNotFoundException) {
                null
            }
        }
            .distinctBy { it.packageName }
            .sortedByDescending { it.dangerousPermissions.size }
    }

    fun openAppDetails(context: Context, packageName: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
