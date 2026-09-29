package com.moodledger.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class TripRange(val id: Long, val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) { "Trip ends before it starts" } }

    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1

    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)

    /** 1-based day number within the trip ("Day 3"), or null outside it. */
    fun dayNumber(date: LocalDate): Int? = if (date in this) ChronoUnit.DAYS.between(start, date).toInt() + 1 else null
}

/** A spending record as far as trip statistics care. Amounts are positive. */
data class TripSpend(
    val date: LocalDate,
    val type: TxnType,
    val category: Category,
    val currency: String,
    val amount: BigDecimal,
    val amountInBase: BigDecimal,
)

data class TripStats(
    val spent: BigDecimal,
    val income: BigDecimal,
    val perDay: BigDecimal,
    val byCategory: List<Pair<Category, BigDecimal>>,
    val byCurrency: List<Pair<String, BigDecimal>>,
    val byDay: Map<LocalDate, BigDecimal>,
    /** Share of budget used (can exceed 1), null without a budget. */
    val budgetUsed: Double?,
    /** Budget left per remaining day, when the trip is ongoing and has a budget. */
    val dailyAllowance: BigDecimal?,
)

data class TravelSummary(val trips: Int, val countries: Int, val continents: Int, val daysAway: Int)

object TripMath {

    /** The trip covering [date]; when trips overlap the one that started last wins (the nested one). */
    fun tripOn(trips: List<TripRange>, date: LocalDate): TripRange? =
        trips.filter { date in it }.maxByOrNull { it.start }

    /**
     * Which diary day a bulk-imported trip photo belongs to: the day it was taken when that falls
     * inside the trip, otherwise today (if on the trip) or the first day.
     */
    fun dayForPhoto(exifDateTime: String?, trip: TripRange, today: LocalDate = LocalDate.now()): LocalDate {
        // EXIF format: "2026:10:03 14:22:05"
        val taken = exifDateTime?.trim()?.takeIf { it.length >= 10 }?.let {
            runCatching { LocalDate.of(it.substring(0, 4).toInt(), it.substring(5, 7).toInt(), it.substring(8, 10).toInt()) }.getOrNull()
        }
        return when {
            taken != null && taken in trip -> taken
            today in trip -> today
            else -> trip.start
        }
    }

    fun stats(
        trip: TripRange,
        spends: List<TripSpend>,
        budgetInBase: BigDecimal? = null,
        today: LocalDate = LocalDate.now(),
    ): TripStats {
        val expenses = spends.filter { it.type == TxnType.EXPENSE }
        val spent = expenses.fold(BigDecimal.ZERO) { a, s -> a + s.amountInBase }
        val income = spends.filter { it.type == TxnType.INCOME }.fold(BigDecimal.ZERO) { a, s -> a + s.amountInBase }
        // Per-day average counts days elapsed so far for an ongoing trip.
        val elapsed = when {
            today.isBefore(trip.start) -> trip.days
            today in trip -> ChronoUnit.DAYS.between(trip.start, today).toInt() + 1
            else -> trip.days
        }
        val budgetUsed = budgetInBase?.takeIf { it.signum() > 0 }
            ?.let { spent.divide(it, 4, RoundingMode.HALF_UP).toDouble() }
        val allowance = if (budgetInBase != null && today in trip) {
            val daysLeft = ChronoUnit.DAYS.between(today, trip.end).toInt() + 1
            (budgetInBase - spent).max(BigDecimal.ZERO).divide(BigDecimal(daysLeft), 2, RoundingMode.HALF_UP)
        } else null
        return TripStats(
            spent = spent,
            income = income,
            perDay = spent.divide(BigDecimal(elapsed), 2, RoundingMode.HALF_UP),
            byCategory = expenses.groupBy { it.category }
                .map { (c, l) -> c to l.fold(BigDecimal.ZERO) { a, s -> a + s.amountInBase } }
                .sortedByDescending { it.second },
            byCurrency = expenses.groupBy { it.currency }
                .map { (c, l) -> c to l.fold(BigDecimal.ZERO) { a, s -> a + s.amount } }
                .sortedByDescending { it.second },
            byDay = expenses.groupBy { it.date }.mapValues { (_, l) -> l.fold(BigDecimal.ZERO) { a, s -> a + s.amountInBase } },
            budgetUsed = budgetUsed,
            dailyAllowance = allowance,
        )
    }

    /**
     * Lifetime travel numbers. Days away counts calendar days covered by at least one trip, so
     * overlapping trips aren't double counted. [countriesByTrip] maps trip id to ISO codes.
     */
    fun summary(
        trips: List<TripRange>,
        countriesByTrip: Map<Long, Set<String>>,
        continentOf: (String) -> String?,
        home: String? = null,
    ): TravelSummary {
        val countries = countriesByTrip.values.flatten().toSet() - setOfNotNull(home)
        val days = trips.flatMap { t -> (0 until t.days).map { t.start.plusDays(it.toLong()) } }.toSet().size
        return TravelSummary(
            trips = trips.size,
            countries = countries.size,
            continents = countries.mapNotNull(continentOf).toSet().size,
            daysAway = days,
        )
    }
}
