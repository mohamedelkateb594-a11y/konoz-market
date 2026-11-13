package com.konoz.market.data.seed

import com.konoz.market.core.PinHasher
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.AppSetting
import com.konoz.market.data.entity.Category
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole

object SettingKeys {
    const val CURRENCY = "currency"
    const val TAX_ENABLED = "tax_enabled"
    const val TAX_BASIS_POINTS = "tax_basis_points"
    const val EXPIRY_ALERT_DAYS = "expiry_alert_days"
    const val ALLOW_NEGATIVE_STOCK = "allow_negative_stock"
    const val CASHIER_MAX_DISCOUNT_BP = "cashier_max_discount_bp"
    const val MUST_CHANGE_ADMIN_PIN = "must_change_admin_pin"
}

/**
 * First-run setup only (idempotent). These are configuration defaults, not demo data:
 * one admin account, a few editable categories and the default settings.
 * The admin PIN is a temporary value and the UI must force changing it (MUST_CHANGE_ADMIN_PIN = true).
 */
object DatabaseInitializer {
    const val DEFAULT_ADMIN_USERNAME = "admin"

    /** The temporary admin PIN. Public so AuthService can refuse it as a "new" PIN. */
    const val TEMP_ADMIN_PIN = "1234"

    private val defaultCategories = listOf(
        "بقالة", "ألبان وأجبان", "مشروبات", "منظفات",
        "لحوم ودواجن", "خضروات وفاكهة", "حلويات وسناكس", "متنوعة",
    )

    private val defaultSettings = mapOf(
        SettingKeys.CURRENCY to "EGP",
        SettingKeys.TAX_ENABLED to "false",
        SettingKeys.TAX_BASIS_POINTS to "0",
        SettingKeys.EXPIRY_ALERT_DAYS to "30",
        SettingKeys.ALLOW_NEGATIVE_STOCK to "true",
        SettingKeys.CASHIER_MAX_DISCOUNT_BP to "1000",
    )

    suspend fun seedIfNeeded(db: KonozDatabase, now: Long = System.currentTimeMillis()) {
        db.tx {
            if (db.users().count() == 0) {
                val salt = PinHasher.newSalt()
                db.users().insert(
                    User(
                        name = "مدير النظام", username = DEFAULT_ADMIN_USERNAME,
                        pinHash = PinHasher.hash(TEMP_ADMIN_PIN, salt), salt = salt,
                        role = UserRole.ADMIN, createdAt = now,
                    )
                )
                db.settings().upsert(AppSetting(SettingKeys.MUST_CHANGE_ADMIN_PIN, "true"))
            }
            if (db.categories().count() == 0) {
                defaultCategories.forEachIndexed { i, name -> db.categories().insert(Category(name = name, sortOrder = i)) }
            }
            for ((k, v) in defaultSettings) {
                if (db.settings().get(k) == null) db.settings().upsert(AppSetting(k, v))
            }
        }
    }
}
