@file:OptIn(ExperimentalMaterial3Api::class)

package com.oinky.app.ui.settings

import android.appwidget.AppWidgetManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import com.oinky.app.ui.components.FaceIcon
import com.oinky.app.ui.components.StickerPickerSheet
import com.oinky.app.ui.components.appContainer
import com.oinky.core.MoodFace
import com.oinky.core.MoodPacks
import android.content.ComponentName
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.oinky.app.AppContainer
import com.oinky.app.ui.components.CurrencyPicker
import com.oinky.app.ui.components.containerViewModel
import com.oinky.app.widget.OinkyWidgetReceiver
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
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
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
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
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
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Evening diary nudge", style = MaterialTheme.typography.titleMedium)
                        Text("After 8pm, if today is still empty", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(reminder, vm::setDiaryReminder)
                }
            }
            MoodFacesCard()
            AppearanceCard()
            WidgetCard()
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

@Composable
private fun WidgetCard() {
    val context = LocalContext.current
    val manager = remember { AppWidgetManager.getInstance(context) }
    var manual by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🐷 Home-screen widget", style = MaterialTheme.typography.titleMedium)
            Text(
                "Log spending and today's mood without opening the app.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = {
                if (manager.isRequestPinAppWidgetSupported) {
                    manager.requestPinAppWidget(ComponentName(context, OinkyWidgetReceiver::class.java), null, null)
                } else {
                    manual = true
                }
            }) { Text("Add to home screen") }
            if (manual) {
                Text(
                    "Your launcher doesn't support adding it from here: long-press the home screen → Widgets → Oinky.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Pick a pack or make each mood face your own: any emoji, or a sticker from your library. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodFacesCard() {
    val settings = appContainer().settings
    val faces by settings.moodFaces.collectAsState()
    var editing by remember { mutableStateOf<MoodFace?>(null) }
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mood faces", style = MaterialTheme.typography.titleLarge)
            Text("Tap a face to change it to any emoji or one of your stickers.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                faces.sortedByDescending { it.score }.forEach { face ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { editing = face }.padding(6.dp),
                    ) {
                        FaceIcon(face, 40.dp)
                        Text(face.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MoodPacks.all.forEach { pack ->
                    FilterChip(
                        selected = faces.sortedByDescending { it.score }.map { it.emoji to it.stickerPath } ==
                            pack.toFaces().map { it.emoji to null },
                        onClick = { settings.setMoodFaces(pack.toFaces()) },
                        label = { Text("${pack.faces.first().first} ${pack.name}") },
                    )
                }
            }
        }
    }
    editing?.let { face ->
        MoodFaceDialog(face, onDismiss = { editing = null }, onSave = { settings.setMoodFace(it); editing = null })
    }
}

@Composable
private fun MoodFaceDialog(initial: MoodFace, onDismiss: () -> Unit, onSave: (MoodFace) -> Unit) {
    var emoji by remember { mutableStateOf(initial.emoji) }
    var label by remember { mutableStateOf(initial.label) }
    var sticker by remember { mutableStateOf(initial.stickerPath) }
    var picking by remember { mutableStateOf(false) }
    val preview = MoodFace(initial.score, emoji, label, sticker)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { FaceIcon(preview, 56.dp) },
        title = { Text("Mood ${initial.score} of 5") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    emoji, { emoji = it.take(8); sticker = null }, label = { Text("Emoji") }, singleLine = true,
                    supportingText = { Text("Use your keyboard's emoji picker") }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(label, { label = it.take(20) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { picking = true }) { Text(if (sticker == null) "Use a sticker" else "Change sticker") }
                    if (sticker != null) TextButton(onClick = { sticker = null }) { Text("Use emoji") }
                }
            }
        },
        confirmButton = { TextButton(enabled = emoji.isNotBlank() && label.isNotBlank(), onClick = { onSave(preview) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) {
        StickerPickerSheet(
            title = "Choose a sticker for “$label”",
            onDismiss = { picking = false },
            onPick = { sticker = it.path; picking = false },
        )
    }
}

@Composable
private fun AppearanceCard() {
    val settings = appContainer().settings
    val wallpaper by settings.wallpaperColors.collectAsState()
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Wallpaper colours", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Use your wallpaper's colours instead of Oinky pink"
                    else "Needs Android 12 or newer",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(wallpaper, settings::setWallpaperColors, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        }
    }
}
