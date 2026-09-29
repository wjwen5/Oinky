@file:OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)

package com.oinky.app.ui.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oinky.app.data.PlaceCandidate
import com.oinky.app.data.TripEntity
import com.oinky.app.data.toMinor
import com.oinky.app.ui.components.CurrencyPicker
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun LocalDate.utcMillis() = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
private fun Long.utcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun TripEditorDialog(
    initial: TripEntity?,
    mainCurrency: String,
    onDismiss: () -> Unit,
    onSave: (TripEntity) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "✈️") }
    var start by remember { mutableStateOf(initial?.start ?: LocalDate.now()) }
    var end by remember { mutableStateOf(initial?.end ?: LocalDate.now().plusDays(4)) }
    var currency by remember { mutableStateOf(initial?.localCurrency ?: mainCurrency) }
    var budgetText by remember { mutableStateOf(initial?.budget?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var budgetCurrency by remember { mutableStateOf(initial?.budgetCurrency ?: mainCurrency) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var pickDates by remember { mutableStateOf(false) }

    val budget = budgetText.replace(",", "").toBigDecimalOrNull()
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New trip" else "Edit trip") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        emoji, { v -> emoji = v.take(4) }, label = { Text("Icon") }, singleLine = true,
                        modifier = Modifier.width(76.dp),
                    )
                    OutlinedTextField(
                        name, { name = it }, label = { Text("Trip name") }, singleLine = true,
                        placeholder = { Text("Japan in autumn") }, modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(onClick = { pickDates = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("${start.format(fmt)} → ${end.format(fmt)}")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Local currency", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Quick entries on trip days default to it",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CurrencyPicker(currency, { currency = it }, extra = listOf(mainCurrency))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        budgetText, { budgetText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Budget (optional)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    CurrencyPicker(budgetCurrency, { budgetCurrency = it }, extra = listOf(mainCurrency, currency))
                }
                OutlinedTextField(note, { note = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete trip (entries and photos are kept)", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val base = initial ?: TripEntity(name = "", startEpochDay = 0, endEpochDay = 0, localCurrency = currency)
                onSave(
                    base.copy(
                        name = name.trim(), emoji = emoji.ifBlank { "✈️" },
                        startEpochDay = start.toEpochDay(), endEpochDay = end.toEpochDay(),
                        localCurrency = currency,
                        budgetMinor = budget?.takeIf { it.signum() > 0 }?.toMinor(),
                        budgetCurrency = if (budget != null) budgetCurrency else null,
                        note = note.trim(),
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickDates) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = start.utcMillis(),
            initialSelectedEndDateMillis = end.utcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDates = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        state.selectedStartDateMillis?.let { s ->
                            start = s.utcDate()
                            end = (state.selectedEndDateMillis ?: s).utcDate()
                        }
                        pickDates = false
                    },
                ) { Text("OK") }
            },
        ) {
            DateRangePicker(state, modifier = Modifier.weight(1f))
        }
    }
}

/** Search countries (offline) and cities (device geocoder) to add as a stop on a trip. */
@Composable
fun AddPlaceDialog(
    search: suspend (String) -> List<PlaceCandidate>,
    onDismiss: () -> Unit,
    onPick: (PlaceCandidate) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceCandidate>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { query }.debounce(350).collectLatest { q ->
            loading = true
            results = runCatching { search(q) }.getOrDefault(emptyList())
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a place") },
        text = {
            Column {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Kyoto, Penang, France…") },
                    trailingIcon = { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results) { c ->
                        ListItem(
                            headlineContent = { Text(c.name) },
                            supportingContent = { Text(c.detail) },
                            modifier = Modifier.clickable { onPick(c) },
                        )
                    }
                }
                if (!loading && query.isNotBlank() && results.isEmpty()) {
                    Text("No matches. Cities need an internet connection; countries work offline.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
