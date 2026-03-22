package com.signalout.android.ui

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages typing indicator preferences.
 * Users can disable sending/receiving typing indicators for privacy.
 */
object TypingIndicatorPreferences {
    private const val PREFS_NAME = "signalout_typing_prefs"
    private const val KEY_ENABLED = "typing_indicators_enabled"

    private var prefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return prefs ?: context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .also { prefs = it }
    }

    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
