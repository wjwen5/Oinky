package com.oinky.core

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CurrencyTest {
    private val table = RateTable("SGD", mapOf("MYR" to 3.25, "USD" to 0.78, "JPY" to 110.0))

    @Test fun `converts via base in both directions`() {
        assertEquals(BigDecimal("40.0000"), table.convert(BigDecimal("130"), "MYR", "SGD"))
        assertEquals(BigDecimal("325.0000"), table.convert(BigDecimal("100"), "SGD", "MYR"))
        // cross rate MYR -> USD = 0.78 / 3.25 = 0.24
        assertEquals(BigDecimal("24.0000"), table.convert(BigDecimal("100"), "MYR", "USD"))
    }

    @Test fun `unknown currency gives null`() {
        assertEquals(null, table.convert(BigDecimal.ONE, "XYZ", "SGD"))
    }

    @Test fun `formatting respects fraction digits`() {
        assertEquals("RM1,234.50", Currencies.format(BigDecimal("1234.5"), "MYR"))
        assertEquals("¥1,500", Currencies.format(BigDecimal("1500.2"), "JPY"))
    }
}

class RecurrenceTest {

    @Test fun `monthly on the 31st clamps and recovers`() {
        val s = Schedule(Frequency.MONTHLY, LocalDate.of(2026, 1, 31))
        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)),
            s.occurrencesBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)),
        )
    }

    @Test fun `next and previous occurrences`() {
        val s = Schedule(Frequency.WEEKLY, LocalDate.of(2026, 9, 1))
        assertEquals(LocalDate.of(2026, 9, 29), s.nextOnOrAfter(LocalDate.of(2026, 9, 23)))
        assertEquals(LocalDate.of(2026, 9, 22), s.previousBefore(LocalDate.of(2026, 9, 23)))
    }

    @Test fun `end date stops the schedule`() {
        val s = Schedule(Frequency.MONTHLY, LocalDate.of(2026, 1, 5), end = LocalDate.of(2026, 3, 1))
        assertEquals(2, s.occurrencesBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)).size)
        assertEquals(null, s.nextOnOrAfter(LocalDate.of(2026, 4, 1)))
    }

    @Test fun `missed payment detection respects windows and grace`() {
        val rent = ExpectedPayment(
            1, "Rent", Schedule(Frequency.MONTHLY, LocalDate.of(2026, 6, 1)), Category.HOUSING, graceDays = 3,
        )
        val txns = listOf(
            TxnRef(LocalDate.of(2026, 6, 1), 1, "Rent", Category.HOUSING),
            TxnRef(LocalDate.of(2026, 6, 29), null, "rent for july", Category.HOUSING), // paid early
            // August missing
            TxnRef(LocalDate.of(2026, 9, 3), null, "Rent", Category.HOUSING), // paid late
        )
        val missed = MissedPaymentDetector.detect(listOf(rent), txns, today = LocalDate.of(2026, 9, 28))
        assertEquals(listOf(LocalDate.of(2026, 8, 1)), missed.map { it.dueDate })
        assertEquals(58, missed.single().daysOverdue)
    }

    @Test fun `grace period suppresses fresh reminders`() {
        val gym = ExpectedPayment(2, "Gym", Schedule(Frequency.MONTHLY, LocalDate.of(2026, 9, 27)), Category.HEALTH, graceDays = 2)
        assertTrue(MissedPaymentDetector.detect(listOf(gym), emptyList(), LocalDate.of(2026, 9, 28)).isEmpty())
        assertEquals(1, MissedPaymentDetector.detect(listOf(gym), emptyList(), LocalDate.of(2026, 9, 30)).size)
    }

    @Test fun `detects a monthly habit`() {
        val history = listOf(
            "Haircut" to LocalDate.of(2026, 5, 10),
            "haircut" to LocalDate.of(2026, 6, 9),
            "Haircut!" to LocalDate.of(2026, 7, 11),
            "haircut" to LocalDate.of(2026, 8, 9),
            "Dinner" to LocalDate.of(2026, 8, 1),
            "Dinner" to LocalDate.of(2026, 8, 3),
            "Dinner" to LocalDate.of(2026, 8, 20),
        )
        val s = RecurrenceDetector.suggest(history)
        assertEquals(1, s.size)
        assertEquals("haircut", s[0].key)
        assertEquals(Frequency.MONTHLY, s[0].frequency)
        assertEquals(LocalDate.of(2026, 9, 9), s[0].nextExpected)
    }
}

class ReceiptParserTest {
    private val parser = ReceiptParser()

    @Test fun `malaysian receipt`() {
        val text = """
            KEDAI MAKAN AH HOCK
            Sdn Bhd (123456-X)
            Tel: 03-1234 5678
            Date: 27/09/2026 19:42
            Nasi Lemak Ayam        12.90
            Teh Tarik x2            7.00
            Sub Total              19.90
            SST 6%                  1.19
            Rounding               -0.04
            TOTAL               RM 21.05
            Cash                   50.00
            Change                 28.95
        """.trimIndent()
        val r = parser.parse(text)
        assertEquals(BigDecimal("21.05"), r.total)
        assertEquals("MYR", r.currency)
        assertEquals("KEDAI MAKAN AH HOCK", r.merchant)
        assertEquals(LocalDate.of(2026, 9, 27), r.date)
        assertEquals(Category.FOOD, r.category)
    }

    @Test fun `singapore receipt with total on next line`() {
        val text = """
            FairPrice Xtra
            NTUC FairPrice Co-operative Ltd
            GST Reg No: M2-0000000-0
            2026-09-20
            Milk 2L          6.45
            Bread            2.80
            GRAND TOTAL
            S$ 9.25
        """.trimIndent()
        val r = parser.parse(text)
        assertEquals(BigDecimal("9.25"), r.total)
        assertEquals("SGD", r.currency)
        assertEquals(LocalDate.of(2026, 9, 20), r.date)
        assertEquals(Category.GROCERIES, r.category)
    }

    @Test fun `falls back to largest amount`() {
        val r = parser.parse("Some Shop\n1x thing 3.00\n1x other 45.50")
        assertNotNull(r.total)
        assertEquals(BigDecimal("45.50"), r.total)
    }
}

class MoodInsightsTest {
    @Test fun `headline compares low and high mood spending`() {
        val days = List(3) { DaySummary(Mood.DOWN, BigDecimal("90")) } +
            List(4) { DaySummary(Mood.GOOD, BigDecimal("30")) } +
            DaySummary(null, BigDecimal("500"))
        val stats = MoodInsights.spendingByMood(days)
        assertEquals(listOf(Mood.GOOD, Mood.DOWN), stats.map { it.mood })
        assertEquals("You spend 3.0× more on low-mood days than on good days.", MoodInsights.headline(stats))
    }
}
