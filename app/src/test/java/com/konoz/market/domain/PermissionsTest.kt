package com.konoz.market.domain

import com.konoz.market.data.entity.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {
    @Test fun admin_can_do_everything() {
        Permission.values().forEach { assertTrue(it.name, Permissions.has(UserRole.ADMIN, it)) }
    }

    @Test fun cashier_cannot_see_profit_edit_prices_adjust_stock_or_manage_users() {
        listOf(
            Permission.VIEW_PROFIT, Permission.EDIT_PRICES, Permission.GIVE_LARGE_DISCOUNT, Permission.ADJUST_STOCK,
            Permission.COUNT_STOCK, Permission.MANAGE_PURCHASES, Permission.MANAGE_USERS,
        ).forEach { assertFalse(it.name, Permissions.has(UserRole.CASHIER, it)) }
    }

    @Test fun cashier_can_take_returns_run_the_drawer_and_record_expenses() {
        listOf(Permission.PROCESS_RETURN, Permission.OPERATE_SHIFTS, Permission.MANAGE_EXPENSES)
            .forEach { assertTrue(it.name, Permissions.has(UserRole.CASHIER, it)) }
    }

    @Test fun stock_keeper_handles_stock_but_not_profit_users_or_the_drawer() {
        listOf(Permission.MANAGE_PURCHASES, Permission.COUNT_STOCK, Permission.ADJUST_STOCK)
            .forEach { assertTrue(it.name, Permissions.has(UserRole.STOCK_KEEPER, it)) }
        listOf(Permission.VIEW_PROFIT, Permission.MANAGE_USERS, Permission.OPERATE_SHIFTS, Permission.EDIT_PRICES)
            .forEach { assertFalse(it.name, Permissions.has(UserRole.STOCK_KEEPER, it)) }
    }
}
