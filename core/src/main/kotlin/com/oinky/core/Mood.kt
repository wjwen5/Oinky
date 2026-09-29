package com.oinky.core

import java.math.BigDecimal
import java.math.RoundingMode

/** Five point mood scale shown as emoji on the calendar. */
enum class Mood(val emoji: String, val label: String, val score: Int) {
    AWESOME("🤩", "Awesome", 5),
    GOOD("😊", "Good", 4),
    OKAY("😐", "Okay", 3),
    DOWN("😔", "Down", 2),
    AWFUL("😫", "Awful", 1);

    companion object {
        fun fromScore(score: Int?): Mood? = entries.firstOrNull { it.score == score }
    }
}

data class DaySummary(val mood: Mood?, val spentInBase: BigDecimal)

data class MoodSpending(val mood: Mood, val days: Int, val averageSpent: BigDecimal)

object MoodInsights {

    /** Average daily spending grouped by mood, highest mood first. Days without a mood are skipped. */
    fun spendingByMood(days: List<DaySummary>): List<MoodSpending> = days
        .filter { it.mood != null }
        .groupBy { it.mood!! }
        .map { (mood, list) ->
            val sum = list.fold(BigDecimal.ZERO) { acc, d -> acc + d.spentInBase }
            MoodSpending(mood, list.size, sum.divide(BigDecimal(list.size), 2, RoundingMode.HALF_UP))
        }
        .sortedByDescending { it.mood.score }

    /**
     * A one-line human readable observation, e.g. "You spend 2.3× more on 😔 days than on 😊 days."
     * Returns null when there is not enough data (needs at least 3 days in each compared group).
     */
    fun headline(stats: List<MoodSpending>): String? {
        val low = stats.filter { it.mood.score <= 2 }
        val high = stats.filter { it.mood.score >= 4 }
        val lowDays = low.sumOf { it.days }
        val highDays = high.sumOf { it.days }
        if (lowDays < 3 || highDays < 3) return null
        fun weighted(list: List<MoodSpending>, n: Int) =
            list.fold(BigDecimal.ZERO) { a, s -> a + s.averageSpent * BigDecimal(s.days) }
                .divide(BigDecimal(n), 4, RoundingMode.HALF_UP)
        val lowAvg = weighted(low, lowDays)
        val highAvg = weighted(high, highDays)
        if (lowAvg.signum() == 0 || highAvg.signum() == 0) return null
        return if (lowAvg > highAvg) {
            "You spend ${lowAvg.divide(highAvg, 1, RoundingMode.HALF_UP)}× more on low-mood days than on good days."
        } else {
            "You spend ${highAvg.divide(lowAvg, 1, RoundingMode.HALF_UP)}× more on good days than on low-mood days."
        }
    }
}
