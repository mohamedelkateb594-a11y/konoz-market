package com.konoz.market.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class PeriodsTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val at = 1_700_000_000_000L // 2023-11-14T22:13:20Z
    private val dayMs = 86_400_000L

    @Test fun day_range_covers_exactly_one_calendar_day() {
        val r = Periods.rangeOf(PeriodKind.DAY, at, utc)
        assertEquals(1_699_920_000_000L, r.from) // 2023-11-14T00:00Z
        assertEquals(r.from + dayMs - 1, r.to)
    }

    @Test fun month_range_runs_from_the_first_to_the_last_millisecond_of_the_month() {
        val r = Periods.rangeOf(PeriodKind.MONTH, at, utc)
        assertEquals(1_698_796_800_000L, r.from) // 2023-11-01T00:00Z
        assertEquals(1_701_388_799_999L, r.to) // 2023-11-30T23:59:59.999Z
    }

    @Test fun year_range_runs_from_january_first_to_december_31() {
        val r = Periods.rangeOf(PeriodKind.YEAR, at, utc)
        assertEquals(1_672_531_200_000L, r.from) // 2023-01-01T00:00Z
        assertEquals(1_704_067_199_999L, r.to) // 2023-12-31T23:59:59.999Z
    }

    @Test fun the_day_boundary_follows_the_given_time_zone() {
        // 22:13Z on Nov 14 is already Nov 15 00:13 at UTC+2, so the local day starts at 22:00Z.
        val r = Periods.rangeOf(PeriodKind.DAY, at, TimeZone.getTimeZone("GMT+02:00"))
        assertEquals(1_699_999_200_000L, r.from)
        assertEquals(r.from + dayMs - 1, r.to)
    }
}
