package com.konoz.market.ui.theme

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/** The user's choice of appearance: follow the phone, or always light, or always dark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Keeps the appearance choice (same small SharedPreferences file as the language choice). The current value is a Compose
 * state, so changing it repaints the whole app at once without recreating the activity.
 */
object ThemePreference {
    private const val PREFS = "konoz_ui_prefs"
    private const val KEY = "theme_mode"

    private val current = mutableStateOf(ThemeMode.SYSTEM)
    val mode: State<ThemeMode> get() = current

    /** Reads the saved choice; call once before the UI is drawn. */
    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        current.value = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode.name).apply()
        current.value = mode
    }

    /** System -> Light -> Dark -> System. */
    fun next(context: Context) {
        val all = ThemeMode.entries
        set(context, all[(current.value.ordinal + 1) % all.size])
    }

    fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}
