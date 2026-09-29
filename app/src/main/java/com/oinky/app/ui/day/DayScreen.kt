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
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import com.oinky.app.data.DaySticker
import com.oinky.app.ui.components.AmountText
import com.oinky.app.ui.components.FaceIcon
import com.oinky.app.ui.components.LocalMoodFaces
import com.oinky.app.ui.components.PaperCard
import com.oinky.app.ui.components.SectionTitle
import com.oinky.app.ui.components.StickerImage
import com.oinky.app.ui.components.StickerPickerSheet
import com.oinky.app.ui.components.TripBadge
import com.oinky.app.ui.theme.Tokens
import androidx.compose.material3.MaterialTheme
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
    var pickingSticker by remember { mutableStateOf(false) }
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Text(s.date.format(DateTimeFormatter.ofPattern("EEEE d")), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            s.date.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.md),
        ) {
            s.trip?.let { trip ->
                item {
                    TripBadge(
                        "${trip.emoji} Day ${s.tripDay} of ${trip.name} · amounts in ${trip.localCurrency}",
                        Modifier.padding(horizontal = Tokens.Space.gutter).clickable { onOpenTrip(trip.id) },
                    )
                }
            }
            item { MoodPicker(s.mood, vm::setMood) }
            item {
                // DESIGN.md `diary-note`: a white page with no visible field chrome.
                TextField(
                    value = note,
                    onValueChange = vm::onNoteChange,
                    placeholder = { Text("Dear diary… how was today?") },
                    minLines = 6,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = MaterialTheme.shapes.large,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.gutter),
                )
            }
            item {
                StickerRow(
                    stickers = s.stickers,
                    onAdd = { pickingSticker = true },
                    onRemove = vm::unstick,
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
                Row(Modifier.padding(end = Tokens.Space.gutter), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Money", Modifier.weight(1f))
                    if (s.spent.signum() > 0) AmountText(s.spent, s.mainCurrency)
                    if (s.income.signum() > 0) AmountText(s.income, s.mainCurrency, TxnType.INCOME, modifier = Modifier.padding(start = Tokens.Space.sm))
                }
            }
            item {
                PaperCard(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.gutter), padded = false) {
                    if (s.txns.isEmpty()) {
                        Text(
                            "No spending logged. Type something like “rm135 on dinner” below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Tokens.Space.md),
                        )
                    }
                    s.txns.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = Tokens.Space.md), color = MaterialTheme.colorScheme.outlineVariant)
                        TxnRow(t, s.mainCurrency) { vm.openEdit(t) }
                    }
                }
            }
            item { Spacer(Modifier.size(Tokens.Space.lg)) }
        }
    }

    if (pickingSticker) {
        StickerPickerSheet(
            title = "Stick a sticker",
            onDismiss = { pickingSticker = false },
            onPick = { vm.stick(it); pickingSticker = false },
        )
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
    val faces = LocalMoodFaces.current.sortedByDescending { it.score }
    Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.gutter)) {
        Text("How do you feel?", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().padding(top = Tokens.Space.sm), horizontalArrangement = Arrangement.SpaceBetween) {
            faces.forEach { face ->
                val mood = Mood.fromScore(face.score) ?: return@forEach
                val isSel = mood == selected
                // DESIGN.md `mood-face` / `mood-face-selected`.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.large)
                        .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSelect(mood) }
                        .padding(Tokens.Space.sm),
                ) {
                    FaceIcon(
                        face, 40.dp,
                        Modifier.scale(if (isSel) 1.12f else if (selected == null) 1f else 0.85f),
                    )
                    Text(
                        face.label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** DESIGN.md `sticker`: stuck stickers in a loose row with their tilt; tap one to peel it off. */
@Composable
private fun StickerRow(stickers: List<DaySticker>, onAdd: () -> Unit, onRemove: (DaySticker) -> Unit) {
    var peeling by remember { mutableStateOf<DaySticker?>(null) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Tokens.Space.gutter),
    ) {
        items(stickers, key = { it.id }) { st ->
            Box(Modifier.clickable { peeling = st }.padding(Tokens.Space.xs)) {
                StickerImage(st.path, 88.dp, st.rotation)
            }
        }
        item {
            Surface(
                onClick = onAdd, shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(if (stickers.isEmpty()) 64.dp else 56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) { Text("✨", fontSize = 22.sp) }
            }
        }
        if (stickers.isEmpty()) {
            item {
                Text(
                    "Add a sticker — paste a cutout\nfrom your photos",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    peeling?.let { st ->
        AlertDialog(
            onDismissRequest = { peeling = null },
            icon = { StickerImage(st.path, 72.dp, st.rotation) },
            title = { Text("Peel off this sticker?") },
            text = { Text("It stays in your sticker library.") },
            confirmButton = { TextButton(onClick = { onRemove(st); peeling = null }) { Text("Peel off") } },
            dismissButton = { TextButton(onClick = { peeling = null }) { Text("Keep") } },
        )
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
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) { Text(t.category.emoji, fontSize = 20.sp) }
        },
        headlineContent = { Text(t.title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                listOfNotNull(
                    t.category.label,
                    t.merchant?.takeIf { it != t.note },
                    if (t.recurringId != null) "🔁 recurring" else null,
                    if (t.receiptPath != null) "🧾 receipt" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                AmountText(t.amount, t.currency, t.type)
                if (t.currency != main) {
                    Text(
                        if (t.converted) "≈ ${money(t.baseAmount, main)}" else "awaiting rate",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
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
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.imePadding()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Tokens.Space.sm, vertical = Tokens.Space.sm)) {
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
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
