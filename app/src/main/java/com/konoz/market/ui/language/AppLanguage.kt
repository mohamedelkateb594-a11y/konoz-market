package com.konoz.market.ui.language

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

/**
 * In-app language choice (Arabic or English), independent of the phone language.
 * Nothing saved yet = follow the phone, exactly as before. The choice is kept in a small SharedPreferences file.
 */
object AppLanguage {
    const val AR = "ar"
    const val EN = "en"
    private const val PREFS = "konoz_ui_prefs"
    private const val KEY = "app_language"

    private fun saved(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it == AR || it == EN }

    /** The language actually shown: the saved one, else the phone language (Arabic or otherwise English). */
    fun effective(context: Context): String =
        saved(context) ?: if (Locale.getDefault().language == AR) AR else EN

    /** Returns [base] using the saved language (and its layout direction); unchanged when nothing is saved. */
    fun wrap(base: Context): Context {
        val tag = saved(base) ?: return base
        val locale = Locale(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Makes the application context (used by services that build texts) follow the saved language too. */
    @Suppress("DEPRECATION")
    fun applyToApplication(app: Context) {
        val tag = saved(app) ?: return
        val locale = Locale(tag)
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
