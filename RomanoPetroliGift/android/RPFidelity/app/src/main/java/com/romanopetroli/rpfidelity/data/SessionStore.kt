package com.romanopetroli.rpfidelity.data

import android.content.Context
import org.json.JSONObject

/** Persistenza locale (SharedPreferences + JSON) di token e utente loggato, stile UserRecipeStore di FrigoZero. */
object SessionStore {
    private const val prefsName = "rpfidelity_session"
    private const val tokenKey = "token"
    private const val userKey = "user_json"

    fun save(context: Context, token: String, userJson: JSONObject) {
        prefs(context).edit()
            .putString(tokenKey, token)
            .putString(userKey, userJson.toString())
            .apply()
    }

    fun loadToken(context: Context): String? = prefs(context).getString(tokenKey, null)

    fun loadUser(context: Context): JSONObject? {
        val raw = prefs(context).getString(userKey, null) ?: return null
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
}
