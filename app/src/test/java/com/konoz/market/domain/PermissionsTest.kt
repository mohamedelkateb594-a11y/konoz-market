package com.konoz.market.domain

import com.konoz.market.data.entity.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {

    @Test fun admin_has_every_permission() {
        for (p in Permission.values()) assertTrue(p.name, Permissions.has(UserRole.ADMIN, p))
    }

    @Test fun cashier_can_sell_run_shifts_and_refund_but_nothing_sensitive() {
        assertTrue(Permissions.has(UserRole.CASHIER, Permission.SELL))
        assertTrue(Permissions.has(UserRole.CASHIER, Permission.MANAGE_SHIFTS))
        assertTrue(Permissions.has(UserRole.CASHIER, Permission.SALES_RETURNS))
        for (p in listOf(
            Permission.VIEW_PROFITS, Permission.VIEW_REPORTS, Permission.MANAGE_EXPENSES, Permission.MANAGE_PRODUCTS,
            Permission.MANAGE_PURCHASES, Permission.ADJUST_STOCK, Permission.COUNT_STOCK, Permission.APPROVE_COUNT,
            Permission.MANAGE_USERS,
        )) assertFalse(p.name, Permissions.has(UserRole.CASHIER, p))
    }

    @Test fun stock_keeper_runs_the_stock_but_cannot_sell_approve_counts_or_see_profits() {
        for (p in listOf(
            Permission.MANAGE_PRODUCTS, Permission.MANAGE_PURCHASES, Permission.VIEW_INVENTORY,
            Permission.ADJUST_STOCK, Permission.COUNT_STOCK,
        )) assertTrue(p.name, Permissions.has(UserRole.STOCK_KEEPER, p))
        for (p in listOf(
            Permission.SELL, Permission.APPROVE_COUNT, Permission.VIEW_PROFITS, Permission.VIEW_REPORTS,
            Permission.MANAGE_USERS, Permission.MANAGE_EXPENSES, Permission.SALES_RETURNS,
        )) assertFalse(p.name, Permissions.has(UserRole.STOCK_KEEPER, p))
    }

    @Test fun profits_count_approval_and_user_management_belong_to_the_admin_alone() {
        for (p in listOf(Permission.VIEW_PROFITS, Permission.APPROVE_COUNT, Permission.MANAGE_USERS)) {
            val holders = UserRole.values().filter { Permissions.has(it, p) }
            assertEquals(p.name, listOf(UserRole.ADMIN), holders)
        }
    }
}
