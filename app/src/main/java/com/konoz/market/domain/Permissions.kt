package com.konoz.market.domain

import com.konoz.market.data.entity.UserRole

/** Things a signed-in user may be allowed to do. What each role gets is decided in [Permissions]. */
enum class Permission {
    /** Sees profit, costs and inventory value: reports and the dashboard. */
    VIEW_PROFIT,
    MANAGE_PURCHASES,
    MANAGE_EXPENSES,
    /** Starts, counts and approves a stock count. */
    COUNT_STOCK,
    /** Damaged / expired write-offs, manual adjustments and the movement log. */
    ADJUST_STOCK,
    PROCESS_RETURN,
    OPERATE_SHIFTS,
    MANAGE_USERS,
    EDIT_PRICES,
    GIVE_LARGE_DISCOUNT,
    /** Products tab: create/edit products, prices, costs, barcodes, CSV import. */
    MANAGE_PRODUCTS,
}

object Permissions {
    private val cashier = setOf(Permission.PROCESS_RETURN, Permission.OPERATE_SHIFTS, Permission.MANAGE_EXPENSES)
    private val stockKeeper = setOf(
        Permission.MANAGE_PURCHASES, Permission.COUNT_STOCK, Permission.ADJUST_STOCK, Permission.MANAGE_PRODUCTS,
    )

    /** The admin may do everything; a cashier only sells, takes returns, records petty expenses and runs the drawer; a stock keeper handles stock and products. */
    fun has(role: UserRole, permission: Permission): Boolean = when (role) {
        UserRole.ADMIN -> true
        UserRole.CASHIER -> permission in cashier
        UserRole.STOCK_KEEPER -> permission in stockKeeper
    }
}
