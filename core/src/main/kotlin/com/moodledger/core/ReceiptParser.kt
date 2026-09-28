package com.moodledger.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month

data class ReceiptData(
    val total: BigDecimal?,
    val currency: String?,
    val merchant: String?,
    val date: LocalDate?,
    val category: Category,
)

/**
 * Heuristic extraction of the useful fields from OCR'd receipt text (ML Kit returns lines top to
 * bottom). Tuned on Malaysian / Singaporean receipts: TOTAL / GRAND TOTAL / JUMLAH / AMOUNT DUE,
 * RM and S$ prefixes, SST (MY) and GST (SG) hints.
 */
class ReceiptParser(
    private val classifier: CategoryClassifier = CategoryClassifier(),
    private val fallbackCurrency: String? = null,
) {

    private val amountRegex = Regex("(?<![\\d.])(\\d{1,3}(?:,\\d{3})*|\\d+)[.,](\\d{2})(?!\\d)")

    private val totalKeywords = Regex(
        "(?i)\\b(grand\\s*total|total\\s*due|amount\\s*due|balance\\s*due|net\\s*total|total\\s*amount|" +
            "total\\s*payable|jumlah(?:\\s*besar)?|total|amount|nett)\\b",
    )
    private val notTotalKeywords = Regex(
        "(?i)(sub\\s*-?\\s*total|subtotal|tax|gst|sst|service|svc|discount|rounding|change|cash|tendered|" +
            "paid|baki|tunai|points|saving|qty|item)",
    )

    fun parse(text: String): ReceiptData {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val total = findTotal(lines)
        val currency = findCurrency(text)
        val merchant = findMerchant(lines)
        val date = findDate(text)
        val category = classifier.classify(listOfNotNull(merchant, text.take(400)).joinToString(" ")).category
        return ReceiptData(total, currency ?: fallbackCurrency, merchant, date, category)
    }

    private fun amountsIn(line: String): List<BigDecimal> = amountRegex.findAll(line).map {
        BigDecimal(it.groupValues[1].replace(",", "") + "." + it.groupValues[2])
    }.toList()

    private fun findTotal(lines: List<String>): BigDecimal? {
        // Score candidate total lines; prefer the strongest keyword, later lines break ties.
        data class Cand(val amount: BigDecimal, val score: Int, val index: Int)
        val cands = mutableListOf<Cand>()
        lines.forEachIndexed { i, line ->
            val kw = totalKeywords.find(line) ?: return@forEachIndexed
            if (notTotalKeywords.containsMatchIn(line) && !line.contains(Regex("(?i)grand|jumlah besar"))) return@forEachIndexed
            val score = when {
                kw.value.contains(Regex("(?i)grand|due|payable|nett?|jumlah")) -> 3
                kw.value.equals("total", ignoreCase = true) -> 2
                else -> 1
            }
            // Amount on the same line, or on the following line (two-column layouts split by OCR).
            val amounts = amountsIn(line).ifEmpty { lines.getOrNull(i + 1)?.let(::amountsIn).orEmpty() }
            amounts.lastOrNull()?.let { cands += Cand(it, score, i) }
        }
        cands.maxWithOrNull(compareBy<Cand> { it.score }.thenBy { it.index })?.let { return it.amount }
        // Fallback: the largest amount on the receipt is usually the total.
        return lines.flatMap(::amountsIn).maxOrNull()
    }

    private fun findCurrency(text: String): String? {
        val t = text.lowercase()
        return when {
            Regex("\\bs\\$|\\bsgd\\b").containsMatchIn(t) -> "SGD"
            Regex("\\brm\\s?\\d|\\bmyr\\b").containsMatchIn(t) -> "MYR"
            Regex("\\bsst\\b|sdn\\.?\\s?bhd|malaysia").containsMatchIn(t) -> "MYR"
            Regex("\\bgst\\b.*\\b(reg|uen)|\\bpte\\.?\\s?ltd|singapore").containsMatchIn(t) -> "SGD"
            Regex("¥|\\bjpy\\b|円").containsMatchIn(t) -> "JPY"
            Regex("฿|\\bthb\\b|baht").containsMatchIn(t) -> "THB"
            Regex("€|\\beur\\b").containsMatchIn(t) -> "EUR"
            Regex("£|\\bgbp\\b").containsMatchIn(t) -> "GBP"
            Regex("\\busd\\b|us\\$").containsMatchIn(t) -> "USD"
            else -> null
        }
    }

    private fun findMerchant(lines: List<String>): String? = lines.take(6).firstOrNull { line ->
        val letters = line.count { it.isLetter() }
        letters >= 3 && letters >= line.length / 2 &&
            !line.contains(Regex("(?i)receipt|invoice|tax|welcome|tel|phone|fax|www|http|@|reg|no\\.|gst|sst|cashier|table"))
    }?.trim()

    private fun findDate(text: String): LocalDate? {
        Regex("\\b(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b").find(text)?.let { m ->
            safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
        }
        // Day first, as used in MY/SG.
        Regex("\\b(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{2,4})\\b").find(text)?.let { m ->
            val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            safeDate(y, m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let { return it }
        }
        Regex("(?i)\\b(\\d{1,2})[\\s-]?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[\\s,-]*(\\d{2,4})\\b")
            .find(text)?.let { m ->
                val mo = MONTHS.indexOf(m.groupValues[2].lowercase()) + 1
                val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
                safeDate(y, mo, m.groupValues[1].toInt())?.let { return it }
            }
        return null
    }

    private fun safeDate(y: Int, m: Int, d: Int) =
        if (m in 1..12 && y in 2000..2100) runCatching { LocalDate.of(y, Month.of(m), d) }.getOrNull() else null

    companion object {
        private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    }
}
