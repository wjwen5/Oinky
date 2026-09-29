package com.oinky.core

import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldMapTest {
    // The real asset shipped with the app; path injected by core/build.gradle.kts.
    private val map by lazy {
        WorldMapData.parse(File(System.getProperty("worldMapAsset")).readText())
    }

    @Test fun `loads all countries and merges duplicate codes`() {
        assertTrue(map.countries.size > 230)
        assertEquals(map.countries.size, map.countries.map { it.iso2 }.toSet().size)
        assertEquals("Australia", map["AU"]!!.name)
    }

    @Test fun `hit testing finds the right country`() {
        assertEquals("FR", map.countryAt(2.35, 48.86)?.iso2) // Paris
        assertEquals("JP", map.countryAt(139.69, 35.69)?.iso2) // Tokyo
        assertEquals("MY", map.countryAt(101.69, 3.14)?.iso2) // Kuala Lumpur
        assertEquals("SG", map.countryAt(103.82, 1.35)?.iso2) // Singapore, a tiny polygon
        assertEquals("BR", map.countryAt(-47.9, -15.8)?.iso2) // Brasília
        assertNull(map.countryAt(-30.0, 30.0)) // Atlantic
    }

    @Test fun `search by name and code`() {
        assertEquals("JP", map.search("jap").first().iso2)
        assertEquals("MY", map.search("MY").first().iso2)
        assertEquals("Singapore", map.search("sing").first().name)
    }

    @Test fun `mercator round trips`() {
        assertEquals(0.5, Mercator.x(0.0), 1e-9)
        assertEquals(0.5, Mercator.y(0.0), 1e-9)
        assertEquals(35.0, Mercator.lat(Mercator.y(35.0)), 1e-9)
        assertEquals(-120.0, Mercator.lon(Mercator.x(-120.0)), 1e-9)
    }

    @Test fun `flags and local currencies`() {
        assertEquals("🇸🇬", Countries.flag("SG"))
        assertEquals("JPY", Countries.currency("JP"))
        assertEquals("MYR", Countries.currency("my"))
        assertEquals("EUR", Countries.currency("FR"))
    }
}

class TripMathTest {
    private val japan = TripRange(1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10))

    private fun spend(day: Int, base: String, cat: Category = Category.FOOD, cur: String = "JPY", amt: String = "1000") =
        TripSpend(LocalDate.of(2026, 10, day), TxnType.EXPENSE, cat, cur, BigDecimal(amt), BigDecimal(base))

    @Test fun `range helpers`() {
        assertEquals(10, japan.days)
        assertEquals(3, japan.dayNumber(LocalDate.of(2026, 10, 3)))
        assertNull(japan.dayNumber(LocalDate.of(2026, 10, 11)))
    }

    @Test fun `photos are filed by EXIF date`() {
        val outside = LocalDate.of(2026, 12, 1)
        assertEquals(LocalDate.of(2026, 10, 3), TripMath.dayForPhoto("2026:10:03 14:22:05", japan, outside))
        assertEquals(japan.start, TripMath.dayForPhoto("2025:01:01 00:00:00", japan, outside))
        assertEquals(LocalDate.of(2026, 10, 5), TripMath.dayForPhoto(null, japan, today = LocalDate.of(2026, 10, 5)))
        assertEquals(japan.start, TripMath.dayForPhoto("garbage", japan, outside))
    }

    @Test fun `nested trip wins`() {
        val osaka = TripRange(2, LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 6))
        assertEquals(2L, TripMath.tripOn(listOf(japan, osaka), LocalDate.of(2026, 10, 5))?.id)
        assertEquals(1L, TripMath.tripOn(listOf(japan, osaka), LocalDate.of(2026, 10, 8))?.id)
    }

    @Test fun `stats for an ongoing trip with budget`() {
        val spends = listOf(
            spend(1, "100"), spend(2, "50", Category.TRANSPORT), spend(2, "30"),
            TripSpend(LocalDate.of(2026, 10, 2), TxnType.EXPENSE, Category.SHOPPING, "SGD", BigDecimal("20"), BigDecimal("20")),
        )
        val s = TripMath.stats(japan, spends, budgetInBase = BigDecimal("1000"), today = LocalDate.of(2026, 10, 2))
        assertEquals(BigDecimal("200"), s.spent)
        assertEquals(BigDecimal("100.00"), s.perDay) // 2 days elapsed
        assertEquals(Category.FOOD, s.byCategory.first().first)
        assertEquals(listOf("JPY", "SGD"), s.byCurrency.map { it.first })
        assertEquals(0.2, s.budgetUsed!!, 1e-9)
        assertEquals(BigDecimal("88.89"), s.dailyAllowance) // 800 left over 9 days
    }

    @Test fun `bookings made ahead count toward the total and budget but not the daily average`() {
        val flight = TripSpend(LocalDate.of(2026, 8, 15), TxnType.EXPENSE, Category.TRAVEL, "SGD", BigDecimal("600"), BigDecimal("600"))
        val hotel = TripSpend(LocalDate.of(2026, 9, 1), TxnType.EXPENSE, Category.TRAVEL, "JPY", BigDecimal("88000"), BigDecimal("800"))
        val dinner = spend(1, "100")
        val s = TripMath.stats(japan, listOf(flight, hotel, dinner), budgetInBase = BigDecimal("3000"), today = LocalDate.of(2026, 10, 1))
        assertEquals(BigDecimal("1500"), s.spent)
        assertEquals(BigDecimal("1400"), s.upfront)
        assertEquals(BigDecimal("100"), s.onTrip)
        assertEquals(BigDecimal("100.00"), s.perDay) // day 1 of the trip; flight/hotel excluded
        assertEquals(0.5, s.budgetUsed!!, 1e-9)
        assertEquals(Category.TRAVEL, s.byCategory.first().first)
        assertEquals(setOf(LocalDate.of(2026, 10, 1)), s.byDay.keys) // journal days only
        assertEquals(BigDecimal("150.00"), s.dailyAllowance) // (3000 - 1500) / 10 days left
    }

    @Test fun `travel summary dedupes overlapping days and home country`() {
        val osaka = TripRange(2, LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 6))
        val kl = TripRange(3, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2))
        val sum = TripMath.summary(
            listOf(japan, osaka, kl),
            mapOf(1L to setOf("JP"), 2L to setOf("JP"), 3L to setOf("MY", "SG")),
            continentOf = { "Asia" },
            home = "SG",
        )
        assertEquals(TravelSummary(trips = 3, countries = 2, continents = 1, daysAway = 12), sum)
    }
}
