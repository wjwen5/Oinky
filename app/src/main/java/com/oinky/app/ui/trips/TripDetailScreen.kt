@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class, ExperimentalLayoutApi::class)

package com.oinky.app.ui.trips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.oinky.app.AppContainer
import com.oinky.app.data.DayEntry
import com.oinky.app.data.PhotoEntity
import com.oinky.app.data.PlaceCandidate
import com.oinky.app.data.TripEntity
import com.oinky.app.data.TripPlaceEntity
import com.oinky.app.data.TxnEntity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import com.oinky.app.data.TxnDraft
import com.oinky.app.ui.components.AmountText
import com.oinky.app.ui.components.TransactionEditorDialog
import com.oinky.core.Category
import com.oinky.app.ui.components.containerViewModel
import com.oinky.app.ui.components.MoodIcon
import com.oinky.app.ui.components.money
import com.oinky.app.ui.theme.incomeColor
import com.oinky.core.Countries
import com.oinky.core.GeoPoint
import com.oinky.core.TripMath
import com.oinky.core.TripSpend
import com.oinky.core.TripStats
import com.oinky.core.TxnType
import com.oinky.core.WorldMapData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class JournalDay(
    val date: LocalDate,
    val dayNumber: Int,
    val entry: DayEntry?,
    val photos: List<PhotoEntity>,
    val spent: java.math.BigDecimal,
)

data class TripDetailState(
    val trip: TripEntity? = null,
    val places: List<TripPlaceEntity> = emptyList(),
    val txns: List<TxnEntity> = emptyList(),
    val journal: List<JournalDay> = emptyList(),
    val photos: List<PhotoEntity> = emptyList(),
    val stats: TripStats? = null,
    val budgetInMain: java.math.BigDecimal? = null,
    val main: String = "SGD",
    val world: WorldMapData? = null,
    val loaded: Boolean = false,
)

class TripDetailViewModel(private val c: AppContainer, private val tripId: Long) : ViewModel() {

    val state: StateFlow<TripDetailState> = c.trips.observeTrip(tripId).flatMapLatest { trip ->
        if (trip == null) return@flatMapLatest flowOf(TripDetailState(loaded = true))
        val from = trip.startEpochDay
        val to = trip.endEpochDay
        combine(
            combine(c.trips.observePlaces(tripId), c.trips.observeTxns(tripId)) { p, t -> p to t },
            combine(c.ledger.observeDays(from, to), c.ledger.observePhotos(from, to)) { d, ph -> d to ph },
            c.settings.mainCurrency,
            flow { emit(c.trips.world()) },
        ) { (places, txns), (days, photos), main, world ->
            val range = trip.range
            val budgetInMain = trip.budget?.let { b -> c.rates.table()?.convert(b, trip.budgetCurrency ?: main, main) }
            val stats = TripMath.stats(
                range,
                txns.map {
                    TripSpend(LocalDate.ofEpochDay(it.epochDay), it.type, it.category, it.currency, it.amount, it.baseAmount)
                },
                budgetInMain,
            )
            val daysByDate = days.associateBy { it.epochDay }
            val photosByDay = photos.groupBy { it.epochDay }
            val lastDay = minOf(to, LocalDate.now().toEpochDay()) // no empty future days in the journal
            val journal = (from..maxOf(from, lastDay)).map { d ->
                val date = LocalDate.ofEpochDay(d)
                JournalDay(date, range.dayNumber(date) ?: 0, daysByDate[d], photosByDay[d].orEmpty(),
                    stats.byDay[date] ?: java.math.BigDecimal.ZERO)
            }
            TripDetailState(trip, places, txns, journal, photos, stats, budgetInMain, main, world, loaded = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailState())

    suspend fun search(q: String) = c.trips.searchPlaces(q)

    fun addPlace(p: PlaceCandidate) = viewModelScope.launch {
        val s = state.value
        c.trips.addPlace(tripId, p)
        // First stop abroad: switch the trip's quick-entry currency to the local one.
        val trip = s.trip ?: return@launch
        if (s.places.isEmpty() && trip.localCurrency == s.main) {
            Countries.currency(p.countryCode)?.let { cur -> c.trips.saveTrip(trip.copy(localCurrency = cur)) }
        }
    }

    fun removePlace(p: TripPlaceEntity) = viewModelScope.launch { c.trips.deletePlace(p) }
    fun save(trip: TripEntity) = viewModelScope.launch { c.trips.saveTrip(trip) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { state.value.trip?.let { c.trips.deleteTrip(it) }; onDone() }
    fun importPhotos(uris: List<android.net.Uri>) = viewModelScope.launch {
        state.value.trip?.let { c.trips.importPhotos(it, uris) }
    }

    suspend fun bookingCandidates(): List<TxnEntity> = state.value.trip?.let { c.trips.bookingCandidates(it) }.orEmpty()

    fun setBookings(candidates: List<TxnEntity>, selected: Set<Long>) = viewModelScope.launch {
        state.value.trip?.let { c.trips.setBookings(it, candidates, selected) }
    }

    /** Saves a new expense linked to this trip regardless of its date (e.g. a flight paid months ahead). */
    fun addBooking(draft: TxnDraft) = viewModelScope.launch { c.ledger.saveTxn(draft.copy(tripId = tripId, tripChosen = true)) }

    fun setCover(uri: android.net.Uri) = viewModelScope.launch { state.value.trip?.let { c.trips.setCover(it, uri) } }
}

@Composable
fun TripDetailScreen(tripId: Long, onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val vm = containerViewModel(key = "trip-$tripId") { TripDetailViewModel(it, tripId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var addingPlace by remember { mutableStateOf(false) }
    var addingBooking by remember { mutableStateOf(false) }
    var linkingBookings by remember { mutableStateOf(false) }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setCover) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) vm.importPhotos(uris)
    }
    val scheme = MaterialTheme.colorScheme
    val trip = s.trip

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.let { "${it.emoji} ${it.name}" } ?: "") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Filled.Image, "Change cover photo")
                    }
                    IconButton(onClick = { editing = true }, enabled = trip != null) { Icon(Icons.Filled.Edit, "Edit trip") }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { padding ->
        if (trip == null) {
            if (s.loaded) Text("This trip was deleted.", Modifier.padding(padding).padding(16.dp))
        } else LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Cover(trip, s.photos.firstOrNull()?.path) }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.large) {
                    WorldMap(
                        world = s.world,
                        fills = s.places.associate { it.countryCode to scheme.primary },
                        landColor = scheme.surfaceVariant, borderColor = scheme.surface, oceanColor = scheme.surfaceContainerLow,
                        pins = s.places.map { MapPin(it.id, GeoPoint(it.lon, it.lat), scheme.tertiary) },
                        focus = s.places.map { GeoPoint(it.lon, it.lat) },
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    )
                }
            }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.places.forEach { p ->
                        InputChip(
                            selected = false, onClick = {},
                            label = { Text("${Countries.flag(p.countryCode)} ${p.name}") },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, "Remove ${p.name}", Modifier.size(16.dp).clickable { vm.removePlace(p) })
                            },
                        )
                    }
                    AssistChip(
                        onClick = { addingPlace = true },
                        label = { Text(if (s.places.isEmpty()) "Where did you go?" else "Add place") },
                        leadingIcon = { Icon(Icons.Filled.AddLocationAlt, null, Modifier.size(18.dp)) },
                    )
                }
            }
            s.stats?.let { st -> item { MoneyCard(st, s.budgetInMain, s.main, trip) } }
            item { SectionTitle("📔 Journal") }
            if (s.journal.isEmpty()) {
                item { Hint("The journal fills in as the trip days arrive.") }
            }
            items(s.journal, key = { "d${it.date}" }) { day -> JournalRow(day, s.main) { onOpenDay(day.date) } }
            item {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("📸 Photos (${s.photos.size})") }
                    AssistChip(
                        onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        label = { Text("Add photos") },
                        leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, null, Modifier.size(18.dp)) },
                    )
                }
            }
            if (s.photos.isEmpty()) {
                item { Hint("Add photos here and each one is filed on the day it was taken.") }
            } else {
                item { PhotoGrid(s.photos) { onOpenDay(LocalDate.ofEpochDay(it.epochDay)) } }
            }
            val range = trip.range
            val (during, ahead) = s.txns.partition { LocalDate.ofEpochDay(it.epochDay) in range }
            item {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("🧳 Booked ahead") }
                    AssistChip(onClick = { linkingBookings = true }, label = { Text("Link existing") })
                    Spacer(Modifier.width(6.dp))
                    AssistChip(
                        onClick = { addingBooking = true }, label = { Text("Add booking") },
                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                    )
                }
            }
            if (ahead.isEmpty()) {
                item {
                    Hint("Flights, hotels and tours paid before the trip. Add them here, or link ones you've already logged, and they'll count toward this trip's total.")
                }
            }
            items(ahead, key = { "b${it.id}" }) { t -> TripTxnRow(t, s.main) { onOpenDay(LocalDate.ofEpochDay(t.epochDay)) } }
            item { SectionTitle("💸 On the trip (${during.size})") }
            if (during.isEmpty()) {
                item { Hint("Entries logged on trip days are added here automatically. On trip days, amounts default to ${trip.localCurrency}.") }
            }
            items(during, key = { "t${it.id}" }) { t -> TripTxnRow(t, s.main) { onOpenDay(LocalDate.ofEpochDay(t.epochDay)) } }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (editing && trip != null) {
        TripEditorDialog(
            initial = trip, mainCurrency = s.main,
            onDismiss = { editing = false },
            onSave = { vm.save(it); editing = false },
            onDelete = { editing = false; vm.delete(onBack) },
        )
    }
    if (addingPlace) {
        AddPlaceDialog(search = vm::search, onDismiss = { addingPlace = false }, onPick = { vm.addPlace(it); addingPlace = false })
    }
    if (addingBooking && trip != null) {
        TransactionEditorDialog(
            initial = TxnDraft(
                date = minOf(LocalDate.now(), trip.start), category = Category.TRAVEL, currency = s.main,
                tripId = trip.id, tripChosen = true,
            ),
            title = "Booking for ${trip.name}",
            mainCurrency = s.main,
            onDismiss = { addingBooking = false },
            onSave = { d, _ -> vm.addBooking(d); addingBooking = false },
            trips = listOf(trip),
        )
    }
    if (linkingBookings && trip != null) {
        LinkBookingsSheet(trip, s.main, load = vm::bookingCandidates, onDismiss = { linkingBookings = false }) { candidates, selected ->
            vm.setBookings(candidates, selected)
            linkingBookings = false
        }
    }
}

@Composable
private fun TripTxnRow(t: TxnEntity, main: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        leadingContent = { Text(t.category.emoji, fontSize = 22.sp) },
        headlineContent = { Text(t.title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(LocalDate.ofEpochDay(t.epochDay).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy"))) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                AmountText(t.amount, t.currency, t.type)
                if (t.currency != main) Text("≈ ${money(t.baseAmount, main)}", style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}

/**
 * Tick the expenses (from a year before the trip to a month after) that belong to it.
 * Travel and accommodation entries are listed first.
 */
@Composable
private fun LinkBookingsSheet(
    trip: TripEntity,
    main: String,
    load: suspend () -> List<TxnEntity>,
    onDismiss: () -> Unit,
    onSave: (List<TxnEntity>, Set<Long>) -> Unit,
) {
    var candidates by remember { mutableStateOf<List<TxnEntity>?>(null) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(trip.id) {
        val list = load()
        candidates = list
        selected = list.filter { it.tripId == trip.id }.map { it.id }.toSet()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Link bookings to ${trip.emoji} ${trip.name}", style = MaterialTheme.typography.titleLarge)
            Text(
                "Expenses from the year before the trip, and the month after, that aren't on another trip.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val list = candidates
            when {
                list == null -> Text("Loading…", Modifier.padding(vertical = 24.dp))
                list.isEmpty() -> Text(
                    "Nothing to link yet. Log the booking on the day you paid (e.g. “sq flight 620”), or use Add booking.",
                    Modifier.padding(vertical = 24.dp),
                )
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.id }) { t ->
                        val checked = t.id in selected
                        ListItem(
                            modifier = Modifier.clickable { selected = if (checked) selected - t.id else selected + t.id },
                            leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                            headlineContent = { Text("${t.category.emoji} ${t.title}") },
                            supportingContent = { Text(LocalDate.ofEpochDay(t.epochDay).format(DateTimeFormatter.ofPattern("d MMM yyyy"))) },
                            trailingContent = { AmountText(t.baseAmount, main, signed = false) },
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(list.orEmpty(), selected) }, enabled = list != null) {
                    Text(if (selected.isEmpty()) "Save" else "Link ${selected.size}")
                }
            }
        }
    }
}

@Composable
private fun Cover(trip: TripEntity, fallbackPhoto: String?) {
    val photo = trip.coverPhotoPath ?: fallbackPhoto
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(190.dp).clip(RoundedCornerShape(20.dp))) {
        if (photo != null) {
            AsyncImage(model = File(photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)),
                ),
                contentAlignment = Alignment.Center,
            ) { Text(trip.emoji, fontSize = 72.sp) }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(trip.name, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(tripDates(trip), color = Color.White.copy(alpha = 0.9f))
            if (trip.note.isNotBlank()) {
                Text(trip.note, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MoneyCard(st: TripStats, budget: java.math.BigDecimal?, main: String, trip: TripEntity) {
    com.oinky.app.ui.components.PaperCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), padded = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Trip total", style = MaterialTheme.typography.labelMedium)
                    Text(money(st.spent, main), style = com.oinky.app.ui.theme.Tokens.Type.amountLg)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Per day there", style = MaterialTheme.typography.labelMedium)
                    Text(money(st.perDay, main), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (st.upfront.signum() > 0) {
                Text(
                    "🧳 Booked ahead ${money(st.upfront, main)} · 💸 On the trip ${money(st.onTrip, main)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val usedShare = st.budgetUsed
            if (budget != null && usedShare != null) {
                val used = usedShare.toFloat()
                LinearProgressIndicator(
                    progress = { used.coerceIn(0f, 1f) },
                    color = if (used > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    buildString {
                        append("${(used * 100).toInt()}% of ${money(budget, main)}")
                        st.dailyAllowance?.let { append(" · ${money(it, main)}/day left to spend") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (st.byCategory.isNotEmpty()) {
                HorizontalDivider()
                val max = st.byCategory.first().second
                st.byCategory.forEach { (cat, amt) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.emoji, modifier = Modifier.width(28.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(cat.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(money(amt, main), style = MaterialTheme.typography.bodyMedium)
                            }
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(
                                    Modifier.fillMaxWidth((amt.toFloat() / max.toFloat()).coerceIn(0.02f, 1f)).fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                }
            }
            if (st.byCurrency.size > 1 || st.byCurrency.firstOrNull()?.first?.let { it != main } == true) {
                HorizontalDivider()
                Text(
                    "Paid in: " + st.byCurrency.joinToString(" · ") { (cur, amt) -> money(amt, cur) },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trip.localCurrency != main) {
                Text("Quick entries on trip days default to ${trip.localCurrency}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun JournalRow(day: JournalDay, main: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Day ${day.dayNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            MoodIcon(day.entry?.moodScore, 32.dp, fallback = "·")
        }
        Column(Modifier.weight(1f)) {
            Row {
                Text(day.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (day.spent.signum() > 0) Text(money(day.spent, main), style = MaterialTheme.typography.labelLarge)
            }
            Text(
                day.entry?.note?.takeIf { it.isNotBlank() } ?: "Tap to write about this day",
                maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                color = if (day.entry?.note.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (day.photos.isNotEmpty()) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    day.photos.take(4).forEach {
                        AsyncImage(
                            model = File(it.path), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    if (day.photos.size > 4) Text("+${day.photos.size - 4}", Modifier.align(Alignment.CenterVertically))
                }
            }
        }
    }
}

@Composable
private fun PhotoGrid(photos: List<PhotoEntity>, onClick: (PhotoEntity) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        photos.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { p ->
                    AsyncImage(
                        model = File(p.path), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { onClick(p) },
                    )
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp))

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp))
