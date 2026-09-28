package com.moodledger.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moodledger.core.Category
import com.moodledger.core.Frequency
import com.moodledger.core.Mood
import com.moodledger.core.RecurringMode
import com.moodledger.core.Schedule
import com.moodledger.core.TxnType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Amounts are stored as fixed-point hundredths so every currency round-trips exactly. */
fun BigDecimal.toMinor(): Long = setScale(2, RoundingMode.HALF_UP).unscaledValue().toLong()
fun Long.fromMinor(): BigDecimal = BigDecimal.valueOf(this, 2)

@Entity(tableName = "day_entries")
data class DayEntry(
    @PrimaryKey val epochDay: Long,
    val moodScore: Int? = null,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val mood: Mood? get() = Mood.fromScore(moodScore)
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

@Entity(tableName = "photos", indices = [Index("epochDay")])
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val path: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "transactions", indices = [Index("epochDay"), Index("recurringId")])
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val type: TxnType,
    val category: Category,
    val amountMinor: Long,
    val currency: String,
    /** Amount in the main currency at the time of recording (or last re-conversion). */
    val baseAmountMinor: Long,
    val baseCurrency: String,
    val rate: Double,
    /** False when no rate was available (offline); the daily worker fixes these up. */
    val converted: Boolean,
    val note: String,
    val merchant: String? = null,
    val recurringId: Long? = null,
    val receiptPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val amount: BigDecimal get() = amountMinor.fromMinor()
    val baseAmount: BigDecimal get() = baseAmountMinor.fromMinor()
    val title: String get() = note.ifBlank { merchant ?: category.label }
}

@Entity(tableName = "recurring_rules")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TxnType,
    val category: Category,
    val amountMinor: Long,
    val currency: String,
    val frequency: Frequency,
    val interval: Int = 1,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val mode: RecurringMode,
    val graceDays: Int = 2,
    /** Comma separated words that identify a manually recorded payment. */
    val keywords: String = "",
    /** AUTO_POST: last occurrence already posted. */
    val lastPostedEpochDay: Long? = null,
    /** REMIND: occurrences on or before this day were handled or dismissed. */
    val ackThroughEpochDay: Long? = null,
    val active: Boolean = true,
) {
    val schedule: Schedule
        get() = Schedule(
            frequency, LocalDate.ofEpochDay(startEpochDay), interval, endEpochDay?.let(LocalDate::ofEpochDay),
        )
    val amount: BigDecimal get() = amountMinor.fromMinor()
    val keywordList: List<String> get() = keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

@Entity(tableName = "rates")
data class RateEntity(
    @PrimaryKey val code: String,
    val perBase: Double,
    val base: String,
    val fetchedAt: Long,
    val source: String,
)

/** A user correction: "whenever I write this phrase, it's this category". */
@Entity(tableName = "category_rules")
data class CategoryRule(
    @PrimaryKey val phrase: String,
    val category: Category,
)
