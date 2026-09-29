@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)

package com.oinky.app.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.oinky.app.AppContainer
import com.oinky.app.data.TripEntity
import com.oinky.app.data.TripPlaceEntity
import com.oinky.app.ui.components.containerViewModel
import com.oinky.app.ui.components.money
import com.oinky.core.CountryShape
import com.oinky.core.Countries
import com.oinky.core.GeoPoint
import com.oinky.core.TravelSummary
import com.oinky.core.TripMath
import com.oinky.core.TxnType
import com.oinky.core.WorldMapData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TripCard(
    val trip: TripEntity,
    val places: List<TripPlaceEntity>,
    val spent: BigDecimal,
    val budgetInMain: BigDecimal?,
) {
    val countries: List<String> get() = places.map { it.countryCode }.distinct()
}

data class TripsState(
    val world: WorldMapData? = null,
    val cards: List<TripCard> = emptyList(),
    val places: List<TripPlaceEntity> = emptyList(),
    val visited: Set<String> = emptySet(),
    val summary: TravelSummary = TravelSummary(0, 0, 0, 0),
    val main: String = "SGD",
    val selected: CountryShape? = null,
)

class TripsViewModel(private val c: AppContainer) : ViewModel() {
    private val selected = MutableStateFlow<CountryShape?>(null)
    private val world = flow { emit(c.trips.world()) }

    val state: StateFlow<TripsState> = combine(
        combine(c.trips.observeTrips(), c.trips.observeAllPlaces(), c.trips.observeAllTripTxns()) { t, p, x -> Triple(t, p, x) },
        world,
        c.settings.mainCurrency,
        selected,
    ) { (trips, places, txns), w, main, sel ->
        val placesByTrip = places.groupBy { it.tripId }
        val spentByTrip = txns.filter { it.type == TxnType.EXPENSE }.groupBy { it.tripId }
            .mapValues { (_, l) -> l.fold(BigDecimal.ZERO) { a, t -> a + t.baseAmount } }
        val table = c.rates.table()
        // Home = the country whose currency is the main currency, when that is unambiguous.
        val home = w.countries.filter { Countries.currency(it.iso2) == main }.singleOrNull()?.iso2
        TripsState(
            world = w,
            cards = trips.map { t ->
                TripCard(
                    t, placesByTrip[t.id].orEmpty(), spentByTrip[t.id] ?: BigDecimal.ZERO,
                    t.budget?.let { b -> table?.convert(b, t.budgetCurrency ?: main, main) },
                )
            },
            places = places,
            visited = places.map { it.countryCode }.toSet() - setOfNotNull(home),
            summary = TripMath.summary(
                trips.map { it.range },
                placesByTrip.mapValues { (_, l) -> l.map { it.countryCode }.toSet() },
                continentOf = { w[it]?.continent },
                home = home,
            ),
            main = main,
            selected = sel,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripsState())

    fun select(country: CountryShape?) {
        selected.value = if (country?.iso2 == selected.value?.iso2) null else country
    }

    fun create(trip: TripEntity, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(c.trips.saveTrip(trip))
    }
}

@Composable
fun TripsScreen(onOpenTrip: (Long) -> Unit) {
    val vm = containerViewModel { TripsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    val shownCards = s.selected?.let { sel -> s.cards.filter { sel.iso2 in it.countries } } ?: s.cards

    Scaffold(
        topBar = { TopAppBar(title = { Text("Travels") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New trip") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    WorldMap(
                        world = s.world,
                        fills = s.visited.associateWith { scheme.primary } +
                            (s.selected?.let { mapOf(it.iso2 to scheme.tertiary) } ?: emptyMap()),
                        landColor = scheme.surfaceVariant,
                        borderColor = scheme.surface,
                        oceanColor = scheme.surfaceContainerLow,
                        pins = s.places.map { MapPin(it.tripId, GeoPoint(it.lon, it.lat), scheme.secondary) },
                        onCountryTap = vm::select,
                        onPinTap = { onOpenTrip(it.id) },
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("${s.summary.countries}", "countries")
                    Stat("${s.summary.continents}", "continents")
                    Stat("${s.summary.trips}", "trips")
                    Stat("${s.summary.daysAway}", "days away")
                }
            }
            s.selected?.let { sel ->
                item {
                    AssistChip(
                        onClick = { vm.select(null) },
                        label = {
                            Text(
                                "${sel.flag} ${sel.name} · " +
                                    if (shownCards.isEmpty()) "not visited yet — tap to clear" else "${shownCards.size} trip(s) — tap to clear",
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            if (s.cards.isEmpty()) {
                item {
                    Text(
                        "Create a trip, add the places you visit, and every diary day, photo and expense " +
                            "within its dates is collected into a travel journal automatically.",
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(shownCards, key = { it.trip.id }) { card -> TripCardView(card, s.main) { onOpenTrip(card.trip.id) } }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (creating) {
        TripEditorDialog(
            initial = null,
            mainCurrency = s.main,
            onDismiss = { creating = false },
            onSave = { trip ->
                creating = false
                vm.create(trip, onOpenTrip)
            },
        )
    }
}

@Composable
private fun Stat(value: String, label: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

fun tripDates(trip: TripEntity): String {
    val sameYear = trip.start.year == trip.end.year
    val f1 = DateTimeFormatter.ofPattern(if (sameYear) "d MMM" else "d MMM yyyy")
    val f2 = DateTimeFormatter.ofPattern("d MMM yyyy")
    return "${trip.start.format(f1)} – ${trip.end.format(f2)} · ${trip.range.days} day${if (trip.range.days > 1) "s" else ""}"
}

@Composable
private fun TripCardView(card: TripCard, main: String, onClick: () -> Unit) {
    val t = card.trip
    val today = LocalDate.now()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(Modifier.fillMaxWidth().height(140.dp)) {
            if (t.coverPhotoPath != null) {
                AsyncImage(
                    model = File(t.coverPhotoPath), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)),
                    ),
                    contentAlignment = Alignment.Center,
                ) { Text(t.emoji, fontSize = 56.sp) }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text("${t.emoji} ${t.name}", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(tripDates(t), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
            }
            if (today in t.range) {
                Text(
                    "NOW", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.countries.joinToString(" ") { Countries.flag(it) }.ifEmpty { "No places yet" }, modifier = Modifier.weight(1f))
                Text(money(card.spent, main), fontWeight = FontWeight.SemiBold)
            }
            card.budgetInMain?.takeIf { it.signum() > 0 }?.let { budget ->
                val used = (card.spent.toFloat() / budget.toFloat())
                LinearProgressIndicator(
                    progress = { used.coerceIn(0f, 1f) },
                    color = if (used > 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${(used * 100).toInt()}% of ${money(budget, main)} budget",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
