package com.moodledger.app.ui.day

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodledger.app.AppContainer
import com.moodledger.app.data.PhotoEntity
import com.moodledger.app.data.RecurringEntity
import com.moodledger.app.data.TripEntity
import com.moodledger.app.data.TxnDraft
import com.moodledger.app.data.TxnEntity
import com.moodledger.app.data.toMinor
import com.moodledger.core.Currencies
import com.moodledger.core.Frequency
import com.moodledger.core.Mood
import com.moodledger.core.ParsedEntry
import com.moodledger.core.QuickEntryParser
import com.moodledger.core.RecurringMode
import com.moodledger.core.TxnType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

data class DayUiState(
    val date: LocalDate,
    val mood: Mood? = null,
    val photos: List<PhotoEntity> = emptyList(),
    val txns: List<TxnEntity> = emptyList(),
    val mainCurrency: String = "SGD",
    val trip: TripEntity? = null,
    /** Trips the editor can link an entry to (all trips, current one first). */
    val trips: List<TripEntity> = emptyList(),
) {
    val tripDay: Int? get() = trip?.range?.dayNumber(date)

    val spent: BigDecimal get() = txns.filter { it.type == TxnType.EXPENSE }.fold(BigDecimal.ZERO) { a, t -> a + t.baseAmount }
    val income: BigDecimal get() = txns.filter { it.type == TxnType.INCOME }.fold(BigDecimal.ZERO) { a, t -> a + t.baseAmount }
}

data class QuickPreview(val parsed: ParsedEntry, val inMain: BigDecimal?, val mainCurrency: String)

data class EditorRequest(val draft: TxnDraft, val title: String, val existing: TxnEntity? = null)

sealed interface DayEvent {
    data class Added(val txnId: Long, val label: String) : DayEvent
    data class Message(val text: String) : DayEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DayViewModel(private val c: AppContainer, epochDay: Long) : ViewModel() {

    val date: LocalDate = LocalDate.ofEpochDay(epochDay)

    val state: StateFlow<DayUiState> = combine(
        c.ledger.observeDay(epochDay),
        c.ledger.observePhotos(epochDay),
        c.ledger.observeTxns(epochDay),
        c.settings.mainCurrency,
        combine(c.trips.observeTripOn(epochDay), c.trips.observeTrips()) { t, all -> t to all },
    ) { day, photos, txns, main, (trip, all) ->
        DayUiState(date, day?.mood, photos, txns, main, trip, all.sortedByDescending { it.id == trip?.id })
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState(date))

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()
    private var noteLoaded = false

    val quickText = MutableStateFlow("")
    private var parser: QuickEntryParser? = null

    val preview: StateFlow<QuickPreview?> = quickText
        .debounce(120)
        .mapLatest { text ->
            val p = parser()?.parse(text) ?: return@mapLatest null
            val main = c.settings.mainCurrency.value
            QuickPreview(p, c.rates.table()?.convert(p.amount, p.currency, main), main)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _editor = MutableStateFlow<EditorRequest?>(null)
    val editor: StateFlow<EditorRequest?> = _editor.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _events = MutableSharedFlow<DayEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DayEvent> = _events

    init {
        viewModelScope.launch {
            _note.value = c.db.days().get(epochDay)?.note.orEmpty()
            noteLoaded = true
            // Autosave the diary text shortly after typing stops.
            _note.drop(1).debounce(600).collect { c.ledger.setNote(epochDay, it) }
        }
    }

    private suspend fun parser(): QuickEntryParser? {
        if (parser == null) parser = c.ledger.quickParser(date)
        return parser
    }

    fun onNoteChange(text: String) {
        if (noteLoaded) _note.value = text
    }

    fun setMood(mood: Mood) = viewModelScope.launch {
        c.ledger.setMood(date.toEpochDay(), if (state.value.mood == mood) null else mood.score)
    }

    fun addPhotos(uris: List<Uri>) = viewModelScope.launch {
        uris.forEach { runCatching { c.ledger.addPhoto(date.toEpochDay(), it) } }
    }

    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch { c.ledger.deletePhoto(photo) }

    /** Saves the quick entry straight away; the snackbar offers undo / edit. */
    fun submitQuick() = viewModelScope.launch {
        val p = parser()?.parse(quickText.value)
        if (p == null) {
            _events.emit(DayEvent.Message("Couldn't find an amount — try “rm135 on dinner”"))
            return@launch
        }
        val id = c.ledger.saveTxn(
            TxnDraft(
                date = p.date, type = p.type, category = p.category, amount = p.amount,
                currency = p.currency, note = p.note, merchant = p.merchant,
            ),
        )
        quickText.value = ""
        val where = if (p.date != date) " on ${p.date}" else ""
        _events.emit(DayEvent.Added(id, "${p.category.emoji} ${p.note.ifBlank { p.category.label }} · ${Currencies.format(p.amount, p.currency)}$where"))
    }

    fun undo(txnId: Long) = viewModelScope.launch {
        c.ledger.getTxn(txnId)?.let { c.ledger.deleteTxn(it) }
    }

    fun openNew() {
        val s = state.value
        _editor.value = EditorRequest(
            TxnDraft(date = date, currency = s.trip?.localCurrency ?: s.mainCurrency, tripId = s.trip?.id),
            "New entry",
        )
    }

    fun openEdit(txn: TxnEntity) {
        _editor.value = EditorRequest(
            TxnDraft(
                id = txn.id, date = LocalDate.ofEpochDay(txn.epochDay), type = txn.type, category = txn.category,
                amount = txn.amount, currency = txn.currency, note = txn.note, merchant = txn.merchant,
                recurringId = txn.recurringId, receiptPath = txn.receiptPath, suggestedCategory = txn.category,
                tripId = txn.tripId, tripChosen = true,
            ),
            "Edit entry", txn,
        )
    }

    fun openEditById(id: Long) = viewModelScope.launch { c.ledger.getTxn(id)?.let(::openEdit) }

    fun save(draft: TxnDraft, repeat: Frequency?) = viewModelScope.launch {
        var d = draft
        if (repeat != null) {
            // Turn it into an auto-recorded rule; this occurrence is the first one.
            val ruleId = c.ledger.saveRecurring(
                RecurringEntity(
                    name = d.note.ifBlank { d.merchant ?: d.category.label }, type = d.type, category = d.category,
                    amountMinor = d.amount.toMinor(), currency = d.currency, frequency = repeat,
                    startEpochDay = d.date.toEpochDay(), mode = RecurringMode.AUTO_POST,
                    lastPostedEpochDay = d.date.toEpochDay(),
                    keywords = listOfNotNull(d.merchant).joinToString(","),
                ),
            )
            d = d.copy(recurringId = ruleId)
        }
        c.ledger.saveTxn(d)
        parser = null // pick up learned category corrections
        _editor.value = null
    }

    fun delete(txn: TxnEntity) = viewModelScope.launch {
        c.ledger.deleteTxn(txn)
        _editor.value = null
    }

    /** OCR a receipt photo, then open the editor pre-filled for the user to confirm. */
    fun scanReceipt(uri: Uri) = viewModelScope.launch {
        _scanning.value = true
        try {
            val file = c.ledger.copyToAppStorage(uri, "receipts")
            val main = state.value.trip?.localCurrency ?: c.settings.mainCurrency.value
            val (receipt, _) = c.scanner.scan(Uri.fromFile(file), c.ledger.classifier(), main)
            if (receipt.total == null) _events.emit(DayEvent.Message("Couldn't read a total — please fill it in"))
            _editor.value = EditorRequest(
                TxnDraft(
                    date = receipt.date?.takeIf { !it.isAfter(LocalDate.now()) } ?: date,
                    category = receipt.category,
                    amount = receipt.total ?: BigDecimal.ZERO,
                    currency = receipt.currency ?: main,
                    note = receipt.merchant.orEmpty(),
                    merchant = receipt.merchant,
                    receiptPath = file.absolutePath,
                    suggestedCategory = receipt.category,
                ),
                "Check scanned receipt",
            )
        } catch (e: Exception) {
            _events.emit(DayEvent.Message("Scan failed: ${e.message}"))
        } finally {
            _scanning.value = false
        }
    }

    /** Cancel the editor; a freshly scanned receipt image that was not saved is discarded. */
    fun cancelEditor() {
        val req = _editor.value
        if (req?.existing == null) req?.draft?.receiptPath?.let { File(it).delete() }
        _editor.value = null
    }

    override fun onCleared() {
        // viewModelScope is gone; flush the latest note on the app scope.
        if (noteLoaded) {
            val text = _note.value
            val day = date.toEpochDay()
            c.appScope.launch { c.ledger.setNote(day, text) }
        }
    }
}
