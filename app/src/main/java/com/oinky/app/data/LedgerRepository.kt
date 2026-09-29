package com.oinky.app.data

import android.content.Context
import android.net.Uri
import com.oinky.app.rates.ExchangeRateRepository
import com.oinky.app.widget.WidgetRefresher
import com.oinky.core.Category
import com.oinky.core.CategoryClassifier
import com.oinky.core.ExpectedPayment
import com.oinky.core.MissedOccurrence
import com.oinky.core.MissedPaymentDetector
import com.oinky.core.ParsedEntry
import com.oinky.core.QuickEntryParser
import com.oinky.core.RecurrenceDetector
import com.oinky.core.RecurrenceSuggestion
import com.oinky.core.RecurringMode
import com.oinky.core.TxnRef
import com.oinky.core.TxnType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** What the UI hands over to create or edit a transaction. */
data class TxnDraft(
    val id: Long = 0,
    val date: LocalDate,
    val type: TxnType = TxnType.EXPENSE,
    val category: Category = Category.OTHER,
    val amount: BigDecimal = BigDecimal.ZERO,
    val currency: String,
    val note: String = "",
    val merchant: String? = null,
    val recurringId: Long? = null,
    val receiptPath: String? = null,
    /** The category the app guessed; if the user changes it we learn the correction. */
    val suggestedCategory: Category? = null,
    val tripId: Long? = null,
    /** False: link to whichever trip covers [date]. True: [tripId] was picked (null = no trip). */
    val tripChosen: Boolean = false,
)

/** Live preview of a quick entry, with the amount in the main currency when a rate is known. */
data class QuickPreview(val parsed: ParsedEntry, val inMain: BigDecimal?, val mainCurrency: String)

class LedgerRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val rates: ExchangeRateRepository,
    private val settings: Settings,
) {
    // ---- Diary ------------------------------------------------------------------------------

    fun observeDays(from: Long, to: Long): Flow<List<DayEntry>> = db.days().observeRange(from, to)
    fun observeDay(day: Long): Flow<DayEntry?> = db.days().observe(day)
    fun observePhotos(day: Long): Flow<List<PhotoEntity>> = db.photos().observeDay(day)
    fun observePhotos(from: Long, to: Long): Flow<List<PhotoEntity>> = db.photos().observeRange(from, to)

    suspend fun setMood(day: Long, score: Int?) {
        val current = db.days().get(day) ?: DayEntry(day)
        db.days().upsert(current.copy(moodScore = score, updatedAt = System.currentTimeMillis()))
        WidgetRefresher.request(context)
    }

    suspend fun setNote(day: Long, note: String) {
        val current = db.days().get(day) ?: DayEntry(day)
        if (current.note == note) return
        db.days().upsert(current.copy(note = note, updatedAt = System.currentTimeMillis()))
        WidgetRefresher.request(context)
    }

    /** Copies a picked image into app storage so it survives the source being deleted. */
    suspend fun addPhoto(day: Long, uri: Uri): Long {
        val file = copyToAppStorage(uri, "photos")
        return db.photos().insert(PhotoEntity(epochDay = day, path = file.absolutePath))
    }

    suspend fun deletePhoto(photo: PhotoEntity) {
        db.photos().delete(photo)
        withContext(Dispatchers.IO) { File(photo.path).delete() }
    }

    suspend fun copyToAppStorage(uri: Uri, folder: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, folder).apply { mkdirs() }
        val out = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        out
    }

    // ---- Money ------------------------------------------------------------------------------

    fun observeTxns(from: Long, to: Long): Flow<List<TxnEntity>> = db.txns().observeRange(from, to)
    fun observeTxns(day: Long): Flow<List<TxnEntity>> = db.txns().observeDay(day)

    suspend fun classifier(): CategoryClassifier =
        CategoryClassifier(db.categoryRules().all().associate { it.phrase to it.category })

    /** On a trip day, amounts without a currency default to the trip's local currency. */
    suspend fun quickParser(pageDate: LocalDate): QuickEntryParser {
        val trip = db.trips().covering(pageDate.toEpochDay())
        return QuickEntryParser(trip?.localCurrency ?: settings.mainCurrency.value, classifier(), today = { pageDate })
    }

    suspend fun saveTxn(draft: TxnDraft): Long {
        val base = settings.mainCurrency.value
        val rate = rates.rate(draft.currency, base, draft.date)
        val entity = TxnEntity(
            id = draft.id,
            epochDay = draft.date.toEpochDay(),
            type = draft.type,
            category = draft.category,
            amountMinor = draft.amount.toMinor(),
            currency = draft.currency,
            baseAmountMinor = rate?.let { draft.amount.multiply(it).toMinor() } ?: 0,
            baseCurrency = base,
            rate = rate?.toDouble() ?: 0.0,
            converted = rate != null,
            note = draft.note.trim(),
            merchant = draft.merchant?.trim()?.takeIf { it.isNotEmpty() },
            recurringId = draft.recurringId,
            receiptPath = draft.receiptPath,
            tripId = if (draft.tripChosen) draft.tripId else db.trips().covering(draft.date.toEpochDay())?.id,
        )
        learnCategory(draft)
        val id = if (draft.id == 0L) {
            db.txns().insert(entity)
        } else {
            val old = db.txns().get(draft.id)
            db.txns().update(entity.copy(createdAt = old?.createdAt ?: entity.createdAt))
            draft.id
        }
        WidgetRefresher.request(context)
        return id
    }

    /**
     * Parses and saves a quick entry like "rm135 on dinner" as of [pageDate] (relative dates such
     * as "yesterday" count from it). Returns null when no amount could be found.
     */
    suspend fun addQuick(text: String, pageDate: LocalDate): Pair<ParsedEntry, Long>? {
        val p = quickParser(pageDate).parse(text) ?: return null
        val id = saveTxn(
            TxnDraft(
                date = p.date, type = p.type, category = p.category, amount = p.amount,
                currency = p.currency, note = p.note, merchant = p.merchant,
            ),
        )
        return p to id
    }

    suspend fun quickPreview(text: String, pageDate: LocalDate, parser: QuickEntryParser? = null): QuickPreview? {
        val p = (parser ?: quickParser(pageDate)).parse(text) ?: return null
        val main = settings.mainCurrency.value
        return QuickPreview(p, rates.table()?.convert(p.amount, p.currency, main), main)
    }

    suspend fun deleteTxn(txn: TxnEntity) {
        db.txns().delete(txn)
        WidgetRefresher.request(context)
    }

    suspend fun getTxn(id: Long) = db.txns().get(id)

    private suspend fun learnCategory(draft: TxnDraft) {
        val suggested = draft.suggestedCategory ?: return
        if (suggested == draft.category) return
        val phrase = (draft.merchant?.takeIf { it.isNotBlank() } ?: draft.note).lowercase().trim()
        if (phrase.length in 2..40) db.categoryRules().upsert(CategoryRule(phrase, draft.category))
    }

    /** Converts entries recorded while offline. */
    suspend fun reconvertPending() {
        val base = settings.mainCurrency.value
        val fixed = db.txns().unconverted().mapNotNull { t ->
            val rate = rates.rate(t.currency, base, LocalDate.ofEpochDay(t.epochDay)) ?: return@mapNotNull null
            t.copy(
                baseAmountMinor = t.amount.multiply(rate).toMinor(), baseCurrency = base,
                rate = rate.toDouble(), converted = true,
            )
        }
        if (fixed.isNotEmpty()) db.txns().updateAll(fixed)
    }

    /**
     * Switching the main currency re-expresses every entry in the new currency. The original
     * amount and currency are never touched, so switching back is lossless.
     */
    suspend fun changeMainCurrency(code: String) {
        settings.setMainCurrency(code)
        rates.refreshIfStale()
        val table = rates.table()
        val updated = db.txns().all().map { t ->
            val r = table?.rate(t.currency, code)
            if (r == null) {
                t.copy(baseCurrency = code, converted = false)
            } else {
                t.copy(
                    baseAmountMinor = t.amount.multiply(r).toMinor(), baseCurrency = code,
                    rate = r.toDouble(), converted = true,
                )
            }
        }
        db.txns().updateAll(updated)
    }

    // ---- Recurring --------------------------------------------------------------------------

    fun observeRecurring(): Flow<List<RecurringEntity>> = db.recurring().observeAll()

    suspend fun saveRecurring(rule: RecurringEntity): Long =
        if (rule.id == 0L) db.recurring().insert(rule) else rule.id.also { db.recurring().update(rule) }

    suspend fun deleteRecurring(rule: RecurringEntity) = db.recurring().delete(rule)

    /** Posts every AUTO_POST occurrence that is due up to [today]. Returns the created entries. */
    suspend fun postDueRecurring(today: LocalDate = LocalDate.now()): List<TxnEntity> {
        val created = mutableListOf<TxnEntity>()
        for (rule in db.recurring().active().filter { it.mode == RecurringMode.AUTO_POST }) {
            val from = rule.lastPostedEpochDay?.let { LocalDate.ofEpochDay(it + 1) } ?: rule.schedule.start
            val due = rule.schedule.occurrencesBetween(from, today)
            if (due.isEmpty()) continue
            for (date in due) {
                val draft = TxnDraft(
                    date = date, type = rule.type, category = rule.category, amount = rule.amount,
                    currency = rule.currency, note = rule.name, recurringId = rule.id,
                )
                val id = saveTxn(draft)
                db.txns().get(id)?.let(created::add)
            }
            db.recurring().update(rule.copy(lastPostedEpochDay = due.last().toEpochDay()))
        }
        return created
    }

    suspend fun missedPayments(today: LocalDate = LocalDate.now()): List<Pair<RecurringEntity, MissedOccurrence>> {
        val rules = db.recurring().active().filter { it.mode == RecurringMode.REMIND }
        if (rules.isEmpty()) return emptyList()
        val txns = db.txns().since(today.minusDays(200).toEpochDay()).map {
            TxnRef(LocalDate.ofEpochDay(it.epochDay), it.recurringId, "${it.note} ${it.merchant.orEmpty()}", it.category)
        }
        return rules.flatMap { rule ->
            val start = rule.ackThroughEpochDay?.let { maxOf(rule.startEpochDay, it + 1) } ?: rule.startEpochDay
            val payment = ExpectedPayment(
                id = rule.id, name = rule.name,
                schedule = rule.schedule.copy(start = LocalDate.ofEpochDay(rule.startEpochDay)),
                category = rule.category, matchKeywords = rule.keywordList, graceDays = rule.graceDays,
            )
            MissedPaymentDetector.detect(listOf(payment), txns, today)
                .filter { it.dueDate.toEpochDay() >= start }
                .map { rule to it }
        }.sortedByDescending { it.second.daysOverdue }
    }

    /** Record a reminded payment using the rule's default amount (editable afterwards). */
    suspend fun recordOccurrence(rule: RecurringEntity, due: LocalDate): Long = saveTxn(
        TxnDraft(
            date = due, type = rule.type, category = rule.category, amount = rule.amount,
            currency = rule.currency, note = rule.name, recurringId = rule.id,
        ),
    )

    /** "Not this time" — stop reminding about occurrences up to [due]. */
    suspend fun dismissOccurrence(rule: RecurringEntity, due: LocalDate) {
        val current = db.recurring().get(rule.id) ?: return
        val ack = maxOf(current.ackThroughEpochDay ?: Long.MIN_VALUE, due.toEpochDay())
        db.recurring().update(current.copy(ackThroughEpochDay = ack))
    }

    suspend fun recurringSuggestions(today: LocalDate = LocalDate.now()): List<RecurrenceSuggestion> {
        val known = db.recurring().active().flatMap { it.keywordList + it.name }.map { it.lowercase() }
        val history = db.txns().since(today.minusDays(400).toEpochDay())
            .filter { it.recurringId == null && it.type == TxnType.EXPENSE }
            .map { it.title to LocalDate.ofEpochDay(it.epochDay) }
        return RecurrenceDetector.suggest(history)
            .filter { s -> known.none { k -> s.key.contains(k) || k.contains(s.key) } }
            .filter { it.nextExpected.isAfter(today.minusDays(60)) }
    }
}
