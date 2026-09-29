package com.oinky.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oinky.core.Category
import com.oinky.core.Frequency
import com.oinky.core.Mood
import com.oinky.core.RecurringMode
import com.oinky.core.Schedule
import com.oinky.core.TripRange
import com.oinky.core.TxnType
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

@Entity(tableName = "transactions", indices = [Index("epochDay"), Index("recurringId"), Index("tripId")])
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
    val tripId: Long? = null,
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

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "✈️",
    val startEpochDay: Long,
    val endEpochDay: Long,
    /** Default currency for quick entries made on trip days, e.g. JPY in Japan. */
    val localCurrency: String,
    val budgetMinor: Long? = null,
    val budgetCurrency: String? = null,
    val coverPhotoPath: String? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val start: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
    val end: LocalDate get() = LocalDate.ofEpochDay(endEpochDay)
    val range: TripRange get() = TripRange(id, start, end)
    val budget: BigDecimal? get() = budgetMinor?.fromMinor()
}

/** A stop on a trip: a city from the geocoder, or a whole country picked from the map list. */
@Entity(tableName = "trip_places", indices = [Index("tripId")])
data class TripPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val countryCode: String,
    val lat: Double,
    val lon: Double,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A sticker in the user's library: a trimmed PNG (usually a transparent cutout). */
@Entity(tableName = "stickers")
data class StickerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A library sticker stuck onto a diary day, with a little tilt for a scrapbook look. */
@Entity(tableName = "day_stickers", indices = [Index("epochDay"), Index("stickerId")])
data class DayStickerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val stickerId: Long,
    val rotation: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A day sticker joined with its image path, for display. */
data class DaySticker(val id: Long, val epochDay: Long, val stickerId: Long, val rotation: Float, val path: String)
