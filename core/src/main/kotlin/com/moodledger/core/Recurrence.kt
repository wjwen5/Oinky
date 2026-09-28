package com.moodledger.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class Frequency(val label: String, val approxDays: Int) {
    DAILY("Daily", 1),
    WEEKLY("Weekly", 7),
    BIWEEKLY("Every 2 weeks", 14),
    MONTHLY("Monthly", 30),
    QUARTERLY("Quarterly", 91),
    YEARLY("Yearly", 365),
}

/**
 * How a recurring rule behaves when an occurrence comes due.
 *  - AUTO_POST: the app records the transaction itself (fixed subscriptions, rent by standing order).
 *  - REMIND: the user must record it (variable bills, "I should top up my EZ-link monthly");
 *    the app nags when an occurrence passes its grace period without a matching record.
 */
enum class RecurringMode { AUTO_POST, REMIND }

data class Schedule(
    val frequency: Frequency,
    val start: LocalDate,
    val interval: Int = 1,
    val end: LocalDate? = null,
) {
    init { require(interval >= 1) }

    /** The n-th occurrence (0-based). Month based rules keep the anchor day, clamped to month end. */
    fun occurrence(n: Long): LocalDate {
        val k = n * interval
        return when (frequency) {
            Frequency.DAILY -> start.plusDays(k)
            Frequency.WEEKLY -> start.plusWeeks(k)
            Frequency.BIWEEKLY -> start.plusWeeks(2 * k)
            Frequency.MONTHLY -> start.plusMonths(k) // plusMonths clamps 31st -> 30th/28th
            Frequency.QUARTERLY -> start.plusMonths(3 * k)
            Frequency.YEARLY -> start.plusYears(k)
        }
    }

    /** All occurrences in [from, to] inclusive. */
    fun occurrencesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(start)) return emptyList()
        val out = mutableListOf<LocalDate>()
        var n = estimateIndex(from)
        while (true) {
            val d = occurrence(n)
            if (d.isAfter(to) || (end != null && d.isAfter(end))) break
            if (!d.isBefore(from)) out += d
            n++
        }
        return out
    }

    fun nextOnOrAfter(date: LocalDate): LocalDate? {
        var n = estimateIndex(date)
        while (true) {
            val d = occurrence(n)
            if (end != null && d.isAfter(end)) return null
            if (!d.isBefore(date)) return d
            n++
        }
    }

    fun previousBefore(date: LocalDate): LocalDate? {
        var result: LocalDate? = null
        var n = maxOf(0, estimateIndex(date) - 1)
        while (true) {
            val d = occurrence(n)
            if (!d.isBefore(date)) return result
            result = d
            n++
        }
    }

    /** Conservative (never too high) starting index so iteration stays cheap for old rules. */
    private fun estimateIndex(date: LocalDate): Long {
        val days = ChronoUnit.DAYS.between(start, date)
        if (days <= 0) return 0
        val step = frequency.approxDays.toLong() * interval
        return maxOf(0, days / step - 2)
    }
}

/** A transaction as far as reminder matching cares. */
data class TxnRef(val date: LocalDate, val recurringId: Long?, val text: String, val category: Category)

data class ExpectedPayment(
    val id: Long,
    val name: String,
    val schedule: Schedule,
    val category: Category,
    /** Extra words that identify a manual record of this payment, e.g. "netflix" or "ezlink". */
    val matchKeywords: List<String> = emptyList(),
    /** Days after the due date before we start reminding. */
    val graceDays: Int = 2,
)

data class MissedOccurrence(val payment: ExpectedPayment, val dueDate: LocalDate, val daysOverdue: Long)

/**
 * Finds REMIND-mode occurrences that have not been recorded. An occurrence counts as recorded
 * when a transaction is linked to the rule, or mentions one of its keywords / its name, and falls
 * inside the occurrence's window: from half a period before the due date until the next due date.
 * Windows let people pay a bit early or late without false alarms.
 */
object MissedPaymentDetector {

    fun detect(
        payments: List<ExpectedPayment>,
        transactions: List<TxnRef>,
        today: LocalDate,
        lookbackDays: Long = 120,
    ): List<MissedOccurrence> = payments.flatMap { p ->
        val s = p.schedule
        val checkFrom = maxOf(s.start, today.minusDays(lookbackDays))
        s.occurrencesBetween(checkFrom, today.minusDays(p.graceDays.toLong()))
            .filterNot { due -> isRecorded(p, due, transactions) }
            .map { due -> MissedOccurrence(p, due, ChronoUnit.DAYS.between(due, today)) }
    }.sortedByDescending { it.daysOverdue }

    fun isRecorded(p: ExpectedPayment, due: LocalDate, transactions: List<TxnRef>): Boolean {
        val period = (p.schedule.frequency.approxDays * p.schedule.interval).toLong()
        val windowStart = due.minusDays(maxOf(1, period / 2))
        val windowEnd = (p.schedule.nextOnOrAfter(due.plusDays(1)) ?: due.plusDays(period)).minusDays(1)
        val words = (p.matchKeywords + p.name).map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        return transactions.any { t ->
            if (t.date.isBefore(windowStart) || t.date.isAfter(windowEnd)) return@any false
            t.recurringId == p.id || words.any { w -> t.text.lowercase().contains(w) }
        }
    }
}

/** Suggestion produced by [RecurrenceDetector]. */
data class RecurrenceSuggestion(
    val key: String,
    val frequency: Frequency,
    val occurrences: Int,
    val lastDate: LocalDate,
    val nextExpected: LocalDate,
)

/**
 * Spots payments the user records by hand at a regular rhythm (e.g. "haircut" every ~30 days)
 * so the app can offer to turn them into a recurring rule.
 */
object RecurrenceDetector {

    fun suggest(transactions: List<Pair<String, LocalDate>>, minOccurrences: Int = 3): List<RecurrenceSuggestion> =
        transactions
            .map { (text, date) -> normalize(text) to date }
            .filter { it.first.isNotBlank() }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (key, dates) ->
                val sorted = dates.distinct().sorted()
                if (sorted.size < minOccurrences) return@mapNotNull null
                val gaps = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toDouble() }
                val mean = gaps.average()
                val freq = Frequency.entries.filter { it != Frequency.DAILY }
                    .minByOrNull { abs(it.approxDays - mean) } ?: return@mapNotNull null
                val tolerance = maxOf(2.0, freq.approxDays * 0.2)
                if (gaps.any { abs(it - freq.approxDays) > tolerance }) return@mapNotNull null
                val last = sorted.last()
                RecurrenceSuggestion(key, freq, sorted.size, last, Schedule(freq, last).occurrence(1))
            }
            .sortedByDescending { it.occurrences }

    private fun normalize(text: String) = text.lowercase()
        .replace(Regex("[^\\p{L} ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
