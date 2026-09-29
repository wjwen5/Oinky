@file:OptIn(ExperimentalMaterial3Api::class)

package com.oinky.app.ui.day

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.oinky.app.data.QuickPreview
import com.oinky.app.data.TxnEntity
import com.oinky.app.ui.components.TransactionEditorDialog
import com.oinky.app.ui.components.containerViewModel
import com.oinky.app.ui.components.money
import com.oinky.app.ui.theme.incomeColor
import com.oinky.core.Mood
import com.oinky.core.TxnType
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun DayScreen(epochDay: Long, onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit, onOpenTrip: (Long) -> Unit = {}) {
    val vm = containerViewModel(key = "day-$epochDay") { DayViewModel(it, epochDay) }
    val s by vm.state.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val quick by vm.quickText.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val editor by vm.editor.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is DayEvent.Message -> snackbar.showSnackbar(e.text)
                is DayEvent.Added -> {
                    val r = snackbar.showSnackbar("Added ${e.label}", actionLabel = "Edit", withDismissAction = true, duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) vm.openEditById(e.txnId)
                }
            }
        }
    }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) vm.addPhotos(uris)
    }
    val pickReceipt = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::scanReceipt)
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takeReceipt = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(vm::scanReceipt)
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "receipt-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri
        takeReceipt.launch(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(s.date.format(DateTimeFormatter.ofPattern("EEEE")), style = MaterialTheme.typography.labelMedium)
                        Text(s.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenDay(s.date.minusDays(1)) }) { Icon(Icons.Filled.ChevronLeft, "Previous day") }
                    IconButton(
                        onClick = { onOpenDay(s.date.plusDays(1)) },
                        enabled = s.date.isBefore(LocalDate.now()),
                    ) { Icon(Icons.Filled.ChevronRight, "Next day") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            QuickEntryBar(
                tripCurrency = s.trip?.localCurrency?.takeIf { it != s.mainCurrency },
                text = quick,
                preview = preview,
                scanning = scanning,
                onTextChange = { vm.quickText.value = it },
                onSubmit = vm::submitQuick,
                onForm = vm::openNew,
                onScanCamera = ::launchCamera,
                onScanGallery = { pickReceipt.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            s.trip?.let { trip ->
                item {
                    AssistChip(
                        onClick = { onOpenTrip(trip.id) },
                        label = { Text("${trip.emoji} Day ${s.tripDay} of ${trip.name} · amounts default to ${trip.localCurrency}") },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            item { MoodPicker(s.mood, vm::setMood) }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = vm::onNoteChange,
                    placeholder = { Text("Dear diary… how was today?") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                )
            }
            item {
                PhotoStrip(
                    paths = s.photos.map { it.path },
                    onAdd = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onDelete = { i -> vm.deletePhoto(s.photos[i]) },
                )
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("💸 Money", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (s.spent.signum() > 0) Text("−${money(s.spent, s.mainCurrency)}", fontWeight = FontWeight.SemiBold)
                    if (s.income.signum() > 0) {
                        Text("  +${money(s.income, s.mainCurrency)}", fontWeight = FontWeight.SemiBold, color = incomeColor())
                    }
                }
            }
            if (s.txns.isEmpty()) {
                item {
                    Text(
                        "No spending logged. Type something like “rm135 on dinner” below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            items(s.txns, key = { it.id }) { t -> TxnRow(t, s.mainCurrency) { vm.openEdit(t) } }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }

    editor?.let { req ->
        val existing = req.existing
        TransactionEditorDialog(
            initial = req.draft,
            title = req.title,
            mainCurrency = s.mainCurrency,
            onDismiss = vm::cancelEditor,
            onSave = { d, repeat -> vm.save(d, repeat) },
            onDelete = if (existing != null) ({ vm.delete(existing) }) else null,
            trips = s.trips,
        )
    }
}

@Composable
private fun MoodPicker(selected: Mood?, onSelect: (Mood) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("How do you feel?", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Mood.entries.forEach { m ->
                val isSel = m == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSelect(m) }
                        .padding(8.dp),
                ) {
                    Text(
                        m.emoji, fontSize = 32.sp,
                        modifier = Modifier.scale(if (isSel) 1.15f else if (selected == null) 1f else 0.85f),
                    )
                    Text(m.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PhotoStrip(paths: List<String>, onAdd: () -> Unit, onDelete: (Int) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        items(paths.size) { i ->
            Box {
                AsyncImage(
                    model = File(paths[i]), contentDescription = "Photo", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
                )
                IconButton(
                    onClick = { onDelete(i) },
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp).padding(2.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                ) { Icon(Icons.Filled.Close, "Remove photo", tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
        item {
            Surface(
                onClick = onAdd, shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.AddAPhoto, "Add photos") }
            }
        }
    }
}

@Composable
private fun TxnRow(t: TxnEntity, main: String, onClick: () -> Unit) {
    val sign = if (t.type == TxnType.INCOME) "+" else "−"
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Text(t.category.emoji, fontSize = 26.sp) },
        headlineContent = { Text(t.title) },
        supportingContent = {
            Text(
                listOfNotNull(
                    t.category.label,
                    t.merchant?.takeIf { it != t.note },
                    if (t.recurringId != null) "🔁 recurring" else null,
                    if (t.receiptPath != null) "🧾 receipt" else null,
                ).joinToString(" · "),
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$sign${money(t.amount, t.currency)}", fontWeight = FontWeight.SemiBold,
                    color = if (t.type == TxnType.INCOME) incomeColor() else MaterialTheme.colorScheme.onSurface,
                )
                if (t.currency != main) {
                    Text(
                        if (t.converted) "≈ ${money(t.baseAmount, main)}" else "awaiting rate",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun QuickEntryBar(
    tripCurrency: String?,
    text: String,
    preview: QuickPreview?,
    scanning: Boolean,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForm: () -> Unit,
    onScanCamera: () -> Unit,
    onScanGallery: () -> Unit,
) {
    var scanMenu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding()) {
        Column(Modifier.navigationBarsPadding().padding(8.dp)) {
            AnimatedVisibility(preview != null && text.isNotBlank()) {
                preview?.let { p ->
                    val e = p.parsed
                    AssistChip(
                        onClick = onSubmit,
                        label = {
                            Text(
                                buildString {
                                    append("${e.category.emoji} ${e.category.label} · ")
                                    append(if (e.type == TxnType.INCOME) "+" else "")
                                    append(money(e.amount, e.currency))
                                    if (e.currency != p.mainCurrency && p.inMain != null) append(" ≈ ${money(p.inMain, p.mainCurrency)}")
                                    if (e.date != LocalDate.now()) append(" · ${e.date.format(DateTimeFormatter.ofPattern("d MMM"))}")
                                },
                            )
                        },
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { scanMenu = true }, enabled = !scanning) {
                        if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Filled.ReceiptLong, "Scan receipt")
                    }
                    DropdownMenu(expanded = scanMenu, onDismissRequest = { scanMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Take photo of receipt") },
                            leadingIcon = { Icon(Icons.Filled.PhotoCamera, null) },
                            onClick = { scanMenu = false; onScanCamera() },
                        )
                        DropdownMenuItem(
                            text = { Text("Pick receipt from gallery") },
                            leadingIcon = { Icon(Icons.Filled.Image, null) },
                            onClick = { scanMenu = false; onScanGallery() },
                        )
                    }
                }
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text(if (tripCurrency != null) "1500 ramen ($tripCurrency)" else "rm135 on dinner") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                if (text.isBlank()) {
                    IconButton(onClick = onForm) { Icon(Icons.Filled.AddCircleOutline, "Add with form") }
                } else {
                    IconButton(onClick = onSubmit) { Icon(Icons.AutoMirrored.Filled.Send, "Add") }
                }
            }
        }
    }
}
