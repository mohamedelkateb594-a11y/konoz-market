package com.konoz.market.ui.language

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language choice (Arabic or English), independent of the phone language.
 * Arabic is the default until the user switches; the choice is kept in a small SharedPreferences file.
 */
object AppLanguage {
    const val AR = "ar"
    const val EN = "en"
    private const val PREFS = "konoz_ui_prefs"
    private const val KEY = "app_language"

    private fun saved(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it == AR || it == EN }

    /** The language actually shown: the one the user chose, otherwise Arabic. */
    fun effective(context: Context): String = saved(context) ?: AR

    /** Returns [base] using the effective language (and its layout direction). */
    fun wrap(base: Context): Context {
        val locale = Locale(effective(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Makes the application context (used by services that build texts) follow the effective language too. */
    @Suppress("DEPRECATION")
    fun applyToApplication(app: Context) {
        val locale = Locale(effective(app))
        Locale.setDefault(locale)
        val res = app.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        res.updateConfiguration(config, res.displayMetrics)
    }

    /** Switches Arabic <-> English, saves it, and recreates the screen so every text and the direction change at once. */
    fun switch(context: Context) {
        val next = if (effective(context) == AR) EN else AR
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next).commit()
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) {
                c.recreate()
                return
            }
            c = c.baseContext
        }
    }
}
