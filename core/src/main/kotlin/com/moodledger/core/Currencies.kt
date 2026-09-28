package com.moodledger.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Maps the many ways people write a currency ("rm", "RM", "ringgit", "S$", "¥") to ISO 4217 codes.
 */
object Currencies {

    /** Commonly used ISO codes shown first in pickers. */
    val common = listOf(
        "SGD", "MYR", "USD", "EUR", "GBP", "JPY", "CNY", "HKD", "TWD", "KRW",
        "THB", "IDR", "PHP", "VND", "INR", "AUD", "NZD", "CAD", "CHF",
    )

    /** Fraction digits for display. Everything else defaults to 2. */
    private val zeroDecimal = setOf("JPY", "KRW", "VND", "IDR", "TWD", "CLP", "ISK", "HUF")

    fun fractionDigits(code: String): Int = if (code in zeroDecimal) 0 else 2

    val symbols: Map<String, String> = mapOf(
        "SGD" to "S$", "MYR" to "RM", "USD" to "$", "EUR" to "€", "GBP" to "£",
        "JPY" to "¥", "CNY" to "CN¥", "HKD" to "HK$", "TWD" to "NT$", "KRW" to "₩",
        "THB" to "฿", "IDR" to "Rp", "PHP" to "₱", "VND" to "₫", "INR" to "₹",
        "AUD" to "A$", "NZD" to "NZ$", "CAD" to "C$", "CHF" to "CHF",
    )

    /**
     * Lower-case alias -> ISO code. Aliases may contain symbols. Ambiguous ones pick the most
     * likely reading for a Southeast Asia based user ("$" is resolved against the main currency
     * by the parser, "¥" defaults to JPY, "rm" is ringgit while "rmb" is yuan).
     */
    val aliases: Map<String, String> = buildMap {
        fun add(code: String, vararg names: String) = names.forEach { put(it.lowercase(), code) }
        add("MYR", "rm", "myr", "ringgit", "ringgits")
        add("SGD", "sgd", "s$", "sg$", "sing dollar", "sing dollars")
        add("USD", "usd", "us$", "u.s.$")
        add("EUR", "eur", "€", "euro", "euros")
        add("GBP", "gbp", "£", "pound", "pounds", "quid")
        add("JPY", "jpy", "¥", "yen", "円")
        add("CNY", "cny", "rmb", "cn¥", "yuan", "元", "块")
        add("HKD", "hkd", "hk$")
        add("TWD", "twd", "nt$", "ntd")
        add("KRW", "krw", "₩", "won")
        add("THB", "thb", "฿", "baht")
        add("IDR", "idr", "rp", "rupiah")
        add("PHP", "php", "₱", "peso", "pesos")
        add("VND", "vnd", "₫", "dong")
        add("INR", "inr", "₹", "rs", "rupee", "rupees")
        add("AUD", "aud", "a$", "au$")
        add("NZD", "nzd", "nz$")
        add("CAD", "cad", "c$", "ca$")
        add("CHF", "chf", "franc", "francs")
    }

    /** Resolve an alias (case-insensitive) to an ISO code. A bare "$" resolves to [dollarDefault]. */
    fun resolve(token: String, dollarDefault: String = "USD"): String? {
        val t = token.trim().lowercase()
        if (t == "$") return dollarDefault
        aliases[t]?.let { return it }
        val upper = t.uppercase()
        return if (upper.length == 3 && upper in symbols) upper else null
    }

    fun format(amount: BigDecimal, code: String): String {
        val scaled = amount.setScale(fractionDigits(code), RoundingMode.HALF_UP)
        val symbol = symbols[code] ?: "$code "
        val sign = if (scaled.signum() < 0) "-" else ""
        val abs = String.format(java.util.Locale.US, "%,." + fractionDigits(code) + "f", scaled.abs())
        return "$sign$symbol$abs"
    }
}

/**
 * Exchange rates quoted against a single base: `rates[X]` is how many units of X one unit of
 * [base] buys (the format used by open.er-api.com and frankfurter).
 */
data class RateTable(val base: String, val rates: Map<String, Double>) {

    fun has(code: String) = code == base || rates.containsKey(code)

    /** Multiplier that turns an amount in [from] into an amount in [to], or null if unknown. */
    fun rate(from: String, to: String): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        val perBaseFrom = if (from == base) 1.0 else rates[from] ?: return null
        val perBaseTo = if (to == base) 1.0 else rates[to] ?: return null
        if (perBaseFrom == 0.0) return null
        return BigDecimal(perBaseTo).divide(BigDecimal(perBaseFrom), MathContext.DECIMAL64)
    }

    fun convert(amount: BigDecimal, from: String, to: String): BigDecimal? =
        rate(from, to)?.let { amount.multiply(it).setScale(4, RoundingMode.HALF_UP) }
}
