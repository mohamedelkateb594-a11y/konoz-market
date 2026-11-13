package com.konoz.market.domain

import com.konoz.market.data.entity.UserRole

/** Sensitive or role-specific capabilities. Selling itself is [SELL]; the rest protect the operations listed in the spec (section 13). */
enum class Permission {
    /** Use the cashier screen. */
    SELL,
    /** Open / close shifts and read their reports. */
    MANAGE_SHIFTS,
    /** Refund a completed sales invoice. */
    SALES_RETURNS,
    /** See profit figures (dashboard, reports). */
    VIEW_PROFITS,
    /** Reports center (it contains profits). */
    VIEW_REPORTS,
    MANAGE_EXPENSES,
    /** Create / edit products, prices, costs, CSV import. */
    MANAGE_PRODUCTS,
    /** Suppliers, purchase invoices and supplier returns. */
    MANAGE_PURCHASES,
    /** Movement log and stock alerts. */
    VIEW_INVENTORY,
    /** Manual stock adjustments, write-offs, unit conversion. */
    ADJUST_STOCK,
    /** Start a stock count and enter counted quantities. */
    COUNT_STOCK,
    /** Approve a count: this is what changes the stock. */
    APPROVE_COUNT,
    MANAGE_USERS,
}

/**
 * Role -> permissions. One place only, so screens never hard-code role checks.
 * Cashiers keep their price/discount limits inside CashierSession (admin-only price override, max discount).
 */
object Permissions {
    private val byRole: Map<UserRole, Set<Permission>> = mapOf(
        UserRole.ADMIN to Permission.values().toSet(),
        UserRole.CASHIER to setOf(Permission.SELL, Permission.MANAGE_SHIFTS, Permission.SALES_RETURNS),
        UserRole.STOCK_KEEPER to setOf(
            Permission.MANAGE_PRODUCTS, Permission.MANAGE_PURCHASES, Permission.VIEW_INVENTORY,
            Permission.ADJUST_STOCK, Permission.COUNT_STOCK,
        ),
    )

    fun has(role: UserRole, permission: Permission): Boolean = permission in of(role)

    fun of(role: UserRole): Set<Permission> = byRole.getValue(role)
}
