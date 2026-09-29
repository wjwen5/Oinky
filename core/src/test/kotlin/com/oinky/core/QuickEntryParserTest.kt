package com.oinky.core

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickEntryParserTest {

    private val today = LocalDate.of(2026, 9, 28) // a Monday
    private val parser = QuickEntryParser("SGD", today = { today })

    private fun parse(s: String) = parser.parse(s)!!

    @Test fun `ringgit dinner example from the spec`() {
        val e = parse("rm135 on dinner")
        assertEquals(BigDecimal("135"), e.amount)
        assertEquals("MYR", e.currency)
        assertTrue(e.currencyExplicit)
        assertEquals(Category.FOOD, e.category)
        assertEquals(TxnType.EXPENSE, e.type)
        assertEquals("Dinner", e.note)
        assertEquals(today, e.date)
    }

    @Test fun `currency after amount and spaced prefix`() {
        assertEquals("MYR", parse("dinner 135 rm").currency)
        assertEquals(BigDecimal("13.50"), parse("RM 13.50 grab to office").amount)
        assertEquals(Category.TRANSPORT, parse("RM 13.50 grab to office").category)
        assertEquals("SGD", parse("S$4.20 kopi").currency)
    }

    @Test fun `no currency falls back to main currency`() {
        val e = parse("lunch 12.5")
        assertEquals("SGD", e.currency)
        assertFalse(e.currencyExplicit)
        assertEquals(Category.FOOD, e.category)
    }

    @Test fun `bare dollar sign means main currency when it is a dollar`() {
        assertEquals("SGD", parse("$12 movie").currency)
        assertEquals("USD", QuickEntryParser("MYR", today = { today }).parse("$12 movie")!!.currency)
    }

    @Test fun `thousands separators and k suffix`() {
        assertEquals(BigDecimal("1500"), parse("¥1,500 ramen").amount)
        assertEquals("JPY", parse("¥1,500 ramen").currency)
        assertEquals(BigDecimal("1200.0"), parse("hotel 1.2k yen").amount)
    }

    @Test fun `income detection`() {
        val salary = parse("salary 5000")
        assertEquals(TxnType.INCOME, salary.type)
        assertEquals(Category.SALARY, salary.category)
        assertEquals(TxnType.INCOME, parse("+50 from mum").type)
        assertEquals(Category.OTHER_INCOME, parse("+50 from mum").category)
        assertEquals(TxnType.INCOME, parse("refund 20 shopee").type)
    }

    @Test fun `relative dates`() {
        assertEquals(today.minusDays(1), parse("yesterday taxi 18").date)
        assertEquals(today.minusDays(3), parse("groceries 80 3 days ago").date)
        assertEquals(LocalDate.of(2026, 9, 25), parse("last friday cinema 15").date)
        assertEquals(LocalDate.of(2026, 3, 12), parse("12/3 dentist 120").date)
        assertEquals(Category.HEALTH, parse("12/3 dentist 120").category)
    }

    @Test fun `on money is not a weekday`() {
        val e = parse("10 on money transfer fee")
        assertEquals(today, e.date)
    }

    @Test fun `merchant extraction`() {
        val e = parse("coffee 6.8 @ starbucks")
        assertEquals("starbucks", e.merchant)
        assertEquals("Coffee", e.note)
        assertEquals(Category.FOOD, e.category)
        assertEquals("Tealive", parse("rm8 bubble tea at Tealive").merchant)
    }

    @Test fun `currency word elsewhere in the sentence`() {
        val e = parse("135 on dinner in ringgit")
        assertEquals("MYR", e.currency)
        assertEquals("Dinner", e.note)
    }

    @Test fun `rmb is yuan not ringgit`() {
        assertEquals("CNY", parse("rmb200 taobao").currency)
        assertEquals(Category.SHOPPING, parse("rmb200 taobao").category)
    }

    @Test fun `no amount returns null`() {
        assertNull(parser.parse("had a lovely dinner"))
        assertNull(parser.parse(""))
    }
}
