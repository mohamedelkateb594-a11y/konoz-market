package com.konoz.market.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AllocationTest {

    @Test fun shares_add_up_exactly_to_the_total() {
        val shares = Allocation.proportional(100, listOf(1L, 1L, 1L))
        assertEquals(listOf(34L, 33L, 33L), shares)
        assertEquals(100L, shares.sum())
    }

    @Test fun a_single_weight_gets_everything() {
        assertEquals(listOf(4050L), Allocation.proportional(4050, listOf(4500L)))
    }

    @Test fun zero_weights_get_zero_shares() {
        assertEquals(listOf(0L, 2000L, 1000L), Allocation.proportional(3000, listOf(0L, 1000L, 500L)))
    }

    @Test fun a_zero_total_or_all_zero_weights_give_zeros() {
        assertEquals(listOf(0L, 0L), Allocation.proportional(0, listOf(5L, 5L)))
        assertEquals(listOf(0L, 0L), Allocation.proportional(100, listOf(0L, 0L)))
    }

    @Test fun portion_is_rounded_half_up() {
        assertEquals(1140L, Allocation.portion(3420, 1, 3))
        assertEquals(3420L, Allocation.portion(3420, 3, 3))
        assertEquals(3L, Allocation.portion(10, 1, 4)) // 2.5 -> 3
        assertEquals(0L, Allocation.portion(10, 1, 0))
    }
}
