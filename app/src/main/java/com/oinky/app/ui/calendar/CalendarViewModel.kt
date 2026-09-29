package com.oinky.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oinky.app.AppContainer
import com.oinky.app.data.RecurringEntity
import com.oinky.core.Category
import com.oinky.core.Countries
import com.oinky.core.TripMath
import com.oinky.core.Mood
import com.oinky.core.TxnType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class ViewMode { MONTH, WEEK }

data class DayCell(
    val date: LocalDate,
    val mood: Mood?,
    val spent: BigDecimal,
    val income: BigDecimal,
    val notePreview: String,
    val photoPath: String?,
    val txnCount: Int,
    /** Flag (or trip emoji) when the day belongs to a trip. */
    val tripMark: String? = null,
)

data class MissedItem(val rule: RecurringEntity, val due: LocalDate, val daysOverdue: Long)

data class PeriodSummary(
    val spent: BigDecimal = BigDecimal.ZERO,
    val income: BigDecimal = BigDecimal.ZERO,
    val averageMood: Mood? = null,
    val moodDays: Int = 0,
    val topCategory: Category? = null,
    val hasUnconverted: Boolean = false,
)

data class CalendarState(
    val mode: ViewMode = ViewMode.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val gridStart: LocalDate = LocalDate.now(),
    val gridEnd: LocalDate = LocalDate.now(),
    val cells: Map<LocalDate, DayCell> = emptyMap(),
    val mainCurrency: String = "SGD",
    val summary: PeriodSummary = PeriodSummary(),
    val missed: List<MissedItem> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val c: AppContainer) : ViewModel() {

    private val mode = MutableStateFlow(ViewMode.MONTH)
    private val anchor = MutableStateFlow(LocalDate.now())
    private val missed = MutableStateFlow<List<MissedItem>>(emptyList())

    val state: StateFlow<CalendarState> = combine(mode, anchor) { m, a -> m to a }
        .flatMapLatest { (m, a) ->
            val (from, to) = gridRange(m, a)
            val (periodFrom, periodTo) = periodRange(m, a)
            combine(
                c.ledger.observeDays(from.toEpochDay(), to.toEpochDay()),
                c.ledger.observeTxns(from.toEpochDay(), to.toEpochDay()),
                c.ledger.observePhotos(from.toEpochDay(), to.toEpochDay()),
                c.settings.mainCurrency,
                combine(missed, c.trips.observeTrips(), c.trips.observeAllPlaces()) { m, t, p -> Triple(m, t, p) },
            ) { days, txns, photos, main, (missedList, trips, places) ->
                val firstPlace = places.groupBy { it.tripId }.mapValues { (_, l) -> l.first() }
                val ranges = trips.map { it.range }
                val tripById = trips.associateBy { it.id }
                val dayMap = days.associateBy { it.date }
                val txnsByDay = txns.groupBy { LocalDate.ofEpochDay(it.epochDay) }
                val photoByDay = photos.groupBy { LocalDate.ofEpochDay(it.epochDay) }
                val cells = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.associateWith { d ->
                    val dayTxns = txnsByDay[d].orEmpty()
                    DayCell(
                        date = d,
                        mood = dayMap[d]?.mood,
                        spent = dayTxns.filter { it.type == TxnType.EXPENSE }.sumOf { it.baseAmount },
                        income = dayTxns.filter { it.type == TxnType.INCOME }.sumOf { it.baseAmount },
                        notePreview = dayMap[d]?.note.orEmpty().trim(),
                        photoPath = photoByDay[d]?.firstOrNull()?.path,
                        txnCount = dayTxns.size,
                        tripMark = TripMath.tripOn(ranges, d)?.let { r ->
                            firstPlace[r.id]?.let { Countries.flag(it.countryCode) } ?: tripById[r.id]?.emoji
                        },
                    )
                }
                val inPeriod = { d: LocalDate -> !d.isBefore(periodFrom) && !d.isAfter(periodTo) }
                val periodTxns = txns.filter { inPeriod(LocalDate.ofEpochDay(it.epochDay)) }
                val moods = days.filter { inPeriod(it.date) }.mapNotNull { it.mood }
                val summary = PeriodSummary(
                    spent = periodTxns.filter { it.type == TxnType.EXPENSE }.sumOf { it.baseAmount },
                    income = periodTxns.filter { it.type == TxnType.INCOME }.sumOf { it.baseAmount },
                    averageMood = if (moods.isEmpty()) null else
                        Mood.fromScore(BigDecimal(moods.sumOf { it.score }).divide(BigDecimal(moods.size), 0, RoundingMode.HALF_UP).toInt()),
                    moodDays = moods.size,
                    topCategory = periodTxns.filter { it.type == TxnType.EXPENSE }
                        .groupBy { it.category }.maxByOrNull { (_, l) -> l.sumOf { it.baseAmount } }?.key,
                    hasUnconverted = periodTxns.any { !it.converted },
                )
                CalendarState(m, a, from, to, cells, main, summary, missedList)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    fun setMode(m: ViewMode) { mode.value = m }
    fun previous() { anchor.value = if (mode.value == ViewMode.MONTH) anchor.value.minusMonths(1) else anchor.value.minusWeeks(1) }
    fun next() { anchor.value = if (mode.value == ViewMode.MONTH) anchor.value.plusMonths(1) else anchor.value.plusWeeks(1) }
    fun today() { anchor.value = LocalDate.now() }

    fun refreshMissed() = viewModelScope.launch {
        missed.value = c.ledger.missedPayments().map { (rule, m) -> MissedItem(rule, m.dueDate, m.daysOverdue) }
    }

    fun record(item: MissedItem) = viewModelScope.launch {
        c.ledger.recordOccurrence(item.rule, item.due)
        refreshMissed().join()
    }

    fun dismiss(item: MissedItem) = viewModelScope.launch {
        c.ledger.dismissOccurrence(item.rule, item.due)
        refreshMissed().join()
    }

    companion object {
        private val weekStart = DayOfWeek.MONDAY

        fun gridRange(m: ViewMode, a: LocalDate): Pair<LocalDate, LocalDate> = when (m) {
            ViewMode.MONTH -> {
                val first = YearMonth.from(a).atDay(1).with(TemporalAdjusters.previousOrSame(weekStart))
                first to first.plusDays(41)
            }
            ViewMode.WEEK -> {
                val first = a.with(TemporalAdjusters.previousOrSame(weekStart))
                first to first.plusDays(6)
            }
        }

        fun periodRange(m: ViewMode, a: LocalDate): Pair<LocalDate, LocalDate> = when (m) {
            ViewMode.MONTH -> YearMonth.from(a).let { it.atDay(1) to it.atEndOfMonth() }
            ViewMode.WEEK -> gridRange(m, a)
        }
    }
}
