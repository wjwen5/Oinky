@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.oinky.app.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.oinky.app.AppContainer
import com.oinky.app.data.RecurringEntity
import com.oinky.app.data.toMinor
import com.oinky.app.ui.components.CurrencyPicker
import com.oinky.app.ui.components.containerViewModel
import com.oinky.app.ui.components.money
import com.oinky.core.Category
import com.oinky.core.Frequency
import com.oinky.core.RecurrenceSuggestion
import com.oinky.core.RecurringMode
import com.oinky.core.TxnType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class RuleRow(val rule: RecurringEntity, val next: LocalDate?, val overdueSince: LocalDate?)

class RecurringViewModel(private val c: AppContainer) : ViewModel() {
    private val overdue = MutableStateFlow<Map<Long, LocalDate>>(emptyMap())
    private val _suggestions = MutableStateFlow<List<RecurrenceSuggestion>>(emptyList())
    val suggestions: StateFlow<List<RecurrenceSuggestion>> = _suggestions.asStateFlow()
    val mainCurrency = c.settings.mainCurrency

    val rows: StateFlow<List<RuleRow>> = combine(c.ledger.observeRecurring(), overdue) { rules, od ->
        val today = LocalDate.now()
        rules.map { RuleRow(it, it.schedule.nextOnOrAfter(today), od[it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() = viewModelScope.launch {
        overdue.value = c.ledger.missedPayments().groupBy { it.first.id }
            .mapValues { (_, list) -> list.minOf { it.second.dueDate } }
        _suggestions.value = c.ledger.recurringSuggestions()
    }

    fun save(rule: RecurringEntity) = viewModelScope.launch {
        c.ledger.saveRecurring(rule)
        if (rule.mode == RecurringMode.AUTO_POST) c.ledger.postDueRecurring()
        refresh()
    }

    fun delete(rule: RecurringEntity) = viewModelScope.launch { c.ledger.deleteRecurring(rule); refresh() }
}

@Composable
fun RecurringScreen() {
    val vm = containerViewModel { RecurringViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val main by vm.mainCurrency.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<RecurringEntity?>(null) }
    LaunchedEffect(Unit) { vm.refresh() }

    fun blank(name: String = "", freq: Frequency = Frequency.MONTHLY, start: LocalDate = LocalDate.now()) = RecurringEntity(
        name = name, type = TxnType.EXPENSE, category = Category.OTHER, amountMinor = 0, currency = main,
        frequency = freq, startEpochDay = start.toEpochDay(), mode = RecurringMode.REMIND, keywords = name,
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Recurring & reminders") }) },
        floatingActionButton = { FloatingActionButton(onClick = { editing = blank() }) { Icon(Icons.Filled.Add, "Add rule") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (suggestions.isNotEmpty()) {
                item { SectionTitle("✨ Spotted a pattern") }
                items(suggestions, key = { "s-" + it.key }) { sug ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "You record “${sug.key}” about ${sug.frequency.label.lowercase()} (${sug.occurrences}×). Remind you next time?",
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { editing = blank(sug.key.replaceFirstChar { it.uppercase() }, sug.frequency, sug.nextExpected) }) {
                                Text("Track")
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Your rules") }
            if (rows.isEmpty()) {
                item {
                    Text(
                        "Add rent, subscriptions or bills. “Auto-record” logs them for you; “Remind me” " +
                            "nudges you when a payment you usually make hasn't been recorded.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(rows, key = { it.rule.id }) { row ->
                val r = row.rule
                ListItem(
                    modifier = Modifier.clickable { editing = r },
                    leadingContent = { Text(r.category.emoji, fontSize = 26.sp) },
                    headlineContent = { Text(r.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Column {
                            Text(
                                "${r.frequency.label} · " +
                                    (if (r.mode == RecurringMode.AUTO_POST) "auto-record" else "remind me") +
                                    (row.next?.let { " · next ${it.format(DateTimeFormatter.ofPattern("d MMM"))}" } ?: ""),
                            )
                            row.overdueSince?.let {
                                Text(
                                    "⚠️ Not recorded since ${it.format(DateTimeFormatter.ofPattern("d MMM"))}",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    trailingContent = { Text(money(r.amount, r.currency)) },
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    editing?.let { rule ->
        RuleEditorDialog(
            rule, main,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
            onDelete = if (rule.id != 0L) ({ vm.delete(rule); editing = null }) else null,
        )
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp))

@Composable
private fun RuleEditorDialog(
    initial: RecurringEntity,
    mainCurrency: String,
    onDismiss: () -> Unit,
    onSave: (RecurringEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial.name) }
    var amountText by remember { mutableStateOf(if (initial.amountMinor == 0L) "" else initial.amount.stripTrailingZeros().toPlainString()) }
    var currency by remember { mutableStateOf(initial.currency) }
    var type by remember { mutableStateOf(initial.type) }
    var category by remember { mutableStateOf(initial.category) }
    var frequency by remember { mutableStateOf(initial.frequency) }
    var mode by remember { mutableStateOf(initial.mode) }
    var start by remember { mutableStateOf(LocalDate.ofEpochDay(initial.startEpochDay)) }
    var grace by remember { mutableStateOf(initial.graceDays.toString()) }
    var keywords by remember { mutableStateOf(initial.keywords) }
    var active by remember { mutableStateOf(initial.active) }
    var pickDate by remember { mutableStateOf(false) }

    val amount = amountText.replace(",", "").toBigDecimalOrNull()
    val valid = name.isNotBlank() && amount != null && amount >= BigDecimal.ZERO

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New recurring payment" else "Edit recurring payment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (e.g. Rent, Netflix)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Usual amount") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    CurrencyPicker(currency, { currency = it }, extra = listOf(mainCurrency))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(type == TxnType.EXPENSE, { type = TxnType.EXPENSE; if (category.type != type) category = Category.OTHER }, { Text("Expense") })
                    FilterChip(type == TxnType.INCOME, { type = TxnType.INCOME; if (category.type != type) category = Category.SALARY }, { Text("Income") })
                }
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.filter { it.type == type }.forEach { c ->
                        FilterChip(c == category, { category = c }, { Text("${c.emoji} ${c.label}") })
                    }
                }
                Text("How often", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Frequency.entries.forEach { f -> FilterChip(frequency == f, { frequency = f }, { Text(f.label) }) }
                }
                OutlinedButton(onClick = { pickDate = true }) {
                    Text("First due: ${start.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}")
                }
                Text("When it's due", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(mode == RecurringMode.AUTO_POST, { mode = RecurringMode.AUTO_POST }, { Text("Auto-record") })
                    FilterChip(mode == RecurringMode.REMIND, { mode = RecurringMode.REMIND }, { Text("Remind me if not recorded") })
                }
                if (mode == RecurringMode.REMIND) {
                    OutlinedTextField(
                        grace, { grace = it.filter(Char::isDigit).take(2) }, label = { Text("Grace days before reminding") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        keywords, { keywords = it }, label = { Text("Match words (comma separated)") },
                        supportingText = { Text("An entry containing any of these counts as paid") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (initial.id != 0L) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Active", modifier = Modifier.weight(1f))
                        Switch(active, { active = it })
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete rule", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val startChanged = start.toEpochDay() != initial.startEpochDay || frequency != initial.frequency
                onSave(
                    initial.copy(
                        name = name.trim(), amountMinor = amount!!.toMinor(), currency = currency, type = type,
                        category = category, frequency = frequency, mode = mode, startEpochDay = start.toEpochDay(),
                        graceDays = grace.toIntOrNull() ?: 2, keywords = keywords, active = active,
                        // Keep lastPosted so editing never re-posts past entries; a new schedule
                        // does reset dismissed reminders.
                        ackThroughEpochDay = if (startChanged) null else initial.ackThroughEpochDay,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { start = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
        ) { DatePicker(state) }
    }
}
