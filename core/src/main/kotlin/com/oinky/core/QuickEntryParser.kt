package com.oinky.core

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Result of parsing a free-text quick entry such as `rm135 on dinner`.
 *
 * [currency] is always set: when the text names no currency it falls back to the parser's
 * default (usually the main currency) and [currencyExplicit] is false.
 */
data class ParsedEntry(
    val amount: BigDecimal,
    val currency: String,
    val currencyExplicit: Boolean,
    val type: TxnType,
    val category: Category,
    val note: String,
    val merchant: String?,
    val date: LocalDate,
)

/**
 * Parses natural quick-entry text into a transaction. Supported forms, in any order:
 *
 *  - amounts with attached or separate currency: `rm135`, `135 rm`, `RM 13.50`, `S$4`, `1.2k yen`,
 *    `¥1,500`, `$12` (a bare `$` means the main currency when it is a dollar, otherwise USD)
 *  - income markers: leading `+`, or words like salary / refund / received
 *  - dates: today, yesterday, `2 days ago`, `last fri`, `on monday`, `12/3`, `2026-03-12`
 *  - merchants: `@starbucks` or `at starbucks`
 *  - everything else becomes the note and drives category detection.
 */
class QuickEntryParser(
    private val defaultCurrency: String,
    private val classifier: CategoryClassifier = CategoryClassifier(),
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    private val dollarDefault = if (defaultCurrency in DOLLAR_CURRENCIES) defaultCurrency else "USD"

    private val currencyPattern: String = Currencies.aliases.keys
        .plus("$")
        .sortedByDescending { it.length }
        .joinToString("|") { Regex.escape(it) }

    // Currency before the number (rm135, RM 135, S$4.50) or after (135rm, 1.2k yen).
    private val amountRegex = Regex(
        "(?i)(?<![\\p{L}\\p{N}.])" +
            "([+-])?\\s?" +
            "(?:($currencyPattern)\\s?)?" +
            "(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?" +
            "(k)?" +
            "(?:\\s?($currencyPattern))?" +
            "(?![\\p{L}\\p{N}])",
    )

    fun parse(input: String): ParsedEntry? {
        var text = input.trim()
        if (text.isEmpty()) return null

        val (date, withoutDate) = extractDate(text)
        text = withoutDate

        val match = pickAmount(text) ?: return null
        val sign = match.groupValues[1]
        val prefixCur = match.groupValues[2]
        val whole = match.groupValues[3].replace(",", "")
        val frac = match.groupValues[4]
        val kilo = match.groupValues[5].isNotEmpty()
        val suffixCur = match.groupValues[6]

        var amount = BigDecimal(if (frac.isNotEmpty()) "$whole.$frac" else whole)
        if (kilo) amount = amount.multiply(BigDecimal(1000))
        if (amount.signum() == 0) return null

        val curToken = prefixCur.ifEmpty { suffixCur }
        val resolved = if (curToken.isNotEmpty()) Currencies.resolve(curToken, dollarDefault) else null

        var rest = text.removeRange(match.range).replace(Regex("\\s+"), " ").trim()

        // A currency word may be separated from the number by the note ("135 on dinner in ringgit").
        var currency = resolved
        if (currency == null) {
            val loose = Regex("(?i)(?:\\bin\\s+)?(?<![\\p{L}])($currencyPattern)(?![\\p{L}])").find(rest)
            if (loose != null) {
                currency = Currencies.resolve(loose.groupValues[1], dollarDefault)
                if (currency != null) rest = rest.removeRange(loose.range).trim()
            }
        }

        var merchant: String? = null
        Regex("(?i)(?:@\\s?|\\bat\\s+)([\\p{L}\\p{N}'&.\\- ]+?)(?=\\s+(?:for|on|with)\\b|$)").find(rest)?.let {
            merchant = it.groupValues[1].trim().takeIf(String::isNotEmpty)
            rest = rest.removeRange(it.range).trim()
        }

        val note = rest
            .replace(Regex("(?i)^(?:spent|paid|pay|bought|buy|got|for|on)\\s+"), "")
            .replace(Regex("(?i)^(?:on|for)\\s+"), "")
            .replace(Regex("(?i)\\s+(?:on|for)$"), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '-', ':')

        val classification = classifier.classify(listOfNotNull(note, merchant).joinToString(" "))
        val incomeWord = INCOME_WORDS.containsMatchIn(note)
        val type = when {
            sign == "+" -> TxnType.INCOME
            sign == "-" -> TxnType.EXPENSE
            classification.category.type == TxnType.INCOME || incomeWord -> TxnType.INCOME
            else -> TxnType.EXPENSE
        }
        val category = when {
            classification.category.type == type -> classification.category
            type == TxnType.INCOME -> Category.OTHER_INCOME
            else -> Category.OTHER
        }

        return ParsedEntry(
            amount = amount,
            currency = currency ?: defaultCurrency,
            currencyExplicit = currency != null,
            type = type,
            category = category,
            note = note.replaceFirstChar { it.uppercase() },
            merchant = merchant,
            date = date,
        )
    }

    /** Prefer an amount that carries a currency; otherwise the first standalone number. */
    private fun pickAmount(text: String): MatchResult? {
        val all = amountRegex.findAll(text).toList()
        return all.firstOrNull { it.groupValues[2].isNotEmpty() || it.groupValues[6].isNotEmpty() }
            ?: all.firstOrNull()
    }

    private fun extractDate(text: String): Pair<LocalDate, String> {
        val now = today()
        fun cut(m: MatchResult) = text.removeRange(m.range).replace(Regex("\\s+"), " ").trim()

        Regex("(?i)\\b(?:on\\s+)?(\\d{4}-\\d{1,2}-\\d{1,2})\\b").find(text)?.let { m ->
            try {
                return LocalDate.parse(m.groupValues[1], DateTimeFormatter.ofPattern("yyyy-M-d")) to cut(m)
            } catch (_: DateTimeParseException) { }
        }
        Regex("(?i)\\b(?:on\\s+)?(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b").find(text)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val yRaw = m.groupValues[3]
            val y = when {
                yRaw.isEmpty() -> now.year
                yRaw.length == 2 -> 2000 + yRaw.toInt()
                else -> yRaw.toInt()
            }
            runCatching { LocalDate.of(y, mo, d) }.getOrNull()?.let { date ->
                val fixed = if (yRaw.isEmpty() && date.isAfter(now)) date.minusYears(1) else date
                return fixed to cut(m)
            }
        }
        Regex("(?i)\\b(day before yesterday)\\b").find(text)?.let { return now.minusDays(2) to cut(it) }
        Regex("(?i)\\b(yesterday|ytd|yday)\\b").find(text)?.let { return now.minusDays(1) to cut(it) }
        Regex("(?i)\\btoday\\b").find(text)?.let { return now to cut(it) }
        Regex("(?i)\\b(\\d{1,2})\\s+days?\\s+ago\\b").find(text)?.let {
            return now.minusDays(it.groupValues[1].toLong()) to cut(it)
        }
        Regex(
            "(?i)\\b(?:last|on)\\s+(mon|tue|wed|thu|fri|sat|sun)" +
                "(?:day|s|rs|sday|nesday|rsday|urday)?\\b",
        ).find(text)?.let { m ->
            val dow = DAY_PREFIX.getValue(m.groupValues[1].lowercase())
            var d = now.minusDays(1)
            while (d.dayOfWeek != dow) d = d.minusDays(1)
            return d to cut(m)
        }
        return now to text
    }

    companion object {
        private val DOLLAR_CURRENCIES = setOf("USD", "SGD", "AUD", "NZD", "CAD", "HKD", "TWD")
        private val INCOME_WORDS = Regex("(?i)\\b(salary|income|received|refund|bonus|got paid|cashback|dividend|reimburse\\w*)\\b")
        private val DAY_PREFIX = mapOf(
            "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
            "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY,
            "sun" to DayOfWeek.SUNDAY,
        )
    }
}
