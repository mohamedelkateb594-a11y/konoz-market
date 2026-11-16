package com.konoz.market.ui.shop

import android.content.Context
import com.konoz.market.R

/**
 * The shop's own name, chosen by the owner in Shop settings and printed on every invoice and receipt.
 * Kept in the same small SharedPreferences file as the language choice, so invoice rendering can read it
 * without touching the database. Until the owner sets one, the app name is used.
 */
object ShopProfile {
    private const val PREFS = "konoz_ui_prefs"
    private const val KEY = "shop_name"
    const val MAX_LENGTH = 40

    /** The saved shop name, or null when none was set. */
    fun name(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.trim()?.takeIf { it.isNotEmpty() }

    /** What invoices show: the shop name, otherwise the app name. */
    fun displayName(context: Context): String = name(context) ?: context.getString(R.string.shop_default_name)

    fun setName(context: Context, value: String) {
        val v = value.trim().take(MAX_LENGTH)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (v.isEmpty()) remove(KEY) else putString(KEY, v)
        }.apply()
    }
}
