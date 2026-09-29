@file:OptIn(ExperimentalMaterial3Api::class)

package com.oinky.app.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.oinky.app.AppContainer
import com.oinky.app.ui.components.CurrencyPicker
import com.oinky.app.ui.components.containerViewModel
import com.oinky.core.Currencies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val main = c.settings.mainCurrency
    val diaryReminder = c.settings.diaryReminder
    val lastUpdated = c.rates.observeLastUpdated().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val table = c.rates.observeTable().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _busy = MutableStateFlow<String?>(null)
    val busy = _busy.asStateFlow()

    fun refresh() = viewModelScope.launch {
        _busy.value = "Updating rates…"
        val r = c.rates.refresh()
        c.ledger.reconvertPending()
        _busy.value = if (r.isSuccess) null else "Couldn't reach the rate service. Using cached rates."
    }

    fun changeMain(code: String) = viewModelScope.launch {
        _busy.value = "Converting all entries to $code…"
        c.ledger.changeMainCurrency(code)
        _busy.value = null
    }

    fun setDiaryReminder(on: Boolean) = c.settings.setDiaryReminder(on)
}

@Composable
fun SettingsScreen() {
    val vm = containerViewModel { SettingsViewModel(it) }
    val main by vm.main.collectAsStateWithLifecycle()
    val reminder by vm.diaryReminder.collectAsStateWithLifecycle()
    val updated by vm.lastUpdated.collectAsStateWithLifecycle()
    val table by vm.table.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Main currency", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Everything is totalled in this currency. Entries keep their original amount, " +
                            "so you can switch any time.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CurrencyPicker(main, { if (it != main) confirm = it })
                        OutlinedTextField(
                            custom, { custom = it.uppercase().filter(Char::isLetter).take(3) },
                            label = { Text("Other ISO code") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = custom.length == 3 && custom != main && (table?.has(custom) ?: true),
                            onClick = { confirm = custom },
                        ) { Text("Use") }
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Exchange rates", style = MaterialTheme.typography.titleMedium)
                    Text(
                        updated?.let { (at, source) ->
                            "Updated ${DateUtils.getRelativeTimeSpanString(at)} from $source"
                        } ?: "Not downloaded yet",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    table?.let { t ->
                        listOf("MYR", "USD", "EUR", "JPY").filter { it != main }.take(3).forEach { code ->
                            t.convert(BigDecimal.ONE, code, main)?.let {
                                Text("1 $code = ${Currencies.format(it, main)}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Button(onClick = { vm.refresh() }, enabled = busy == null) { Text("Refresh now") }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Evening diary nudge", style = MaterialTheme.typography.titleMedium)
                        Text("After 8pm, if today is still empty", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(reminder, vm::setDiaryReminder)
                }
            }
            busy?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }

    confirm?.let { code ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Switch to $code?") },
            text = { Text("All entries will be re-converted to $code using the latest rates.") },
            confirmButton = { TextButton(onClick = { vm.changeMain(code); confirm = null; custom = "" }) { Text("Switch") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
