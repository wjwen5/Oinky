@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.oinky.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.oinky.app.data.StickerEntity
import com.oinky.app.data.StickerImport
import com.oinky.core.MoodFace
import com.oinky.core.MoodPacks
import kotlinx.coroutines.launch
import java.io.File

/** The user's five mood faces, provided once at the root from Settings. */
val LocalMoodFaces = staticCompositionLocalOf { MoodPacks.all.first().toFaces() }

@Composable
fun ProvideMoodFaces(content: @Composable () -> Unit) {
    val faces by appContainer().settings.moodFaces.collectAsState()
    CompositionLocalProvider(LocalMoodFaces provides faces, content = content)
}

@Composable
fun moodFace(score: Int?): MoodFace? = score?.let { s -> LocalMoodFaces.current.firstOrNull { it.score == s } }

/** Draws a mood as the user's chosen emoji or sticker; [fallback] text when there's no mood. */
@Composable
fun MoodIcon(score: Int?, size: Dp, modifier: Modifier = Modifier, fallback: String = "") {
    val face = moodFace(score)
    FaceIcon(face, size, modifier, fallback)
}

@Composable
fun FaceIcon(face: MoodFace?, size: Dp, modifier: Modifier = Modifier, fallback: String = "") {
    val sticker = face?.stickerPath
    if (sticker != null) {
        AsyncImage(
            model = File(sticker), contentDescription = face?.label,
            contentScale = ContentScale.Fit, modifier = modifier.size(size),
        )
    } else {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            // Emoji glyphs render a little larger than their font size; 0.8 keeps them inside the box.
            Text(face?.emoji ?: fallback, fontSize = (size.value * 0.8f).sp)
        }
    }
}

/** A sticker drawn with its scrapbook tilt. */
@Composable
fun StickerImage(path: String, size: Dp, rotation: Float = 0f, modifier: Modifier = Modifier) {
    AsyncImage(
        model = File(path), contentDescription = "Sticker", contentScale = ContentScale.Fit,
        modifier = modifier.size(size).rotate(rotation),
    )
}

/**
 * Bottom sheet with the sticker library. Tap to choose; long-press to delete. "Paste" reads a
 * cutout copied from the gallery (long-press the subject → Copy) or a sticker copied from a chat.
 */
@Composable
fun StickerPickerSheet(
    title: String,
    onDismiss: () -> Unit,
    onPick: (StickerEntity) -> Unit,
) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val library by remember { c.stickers.observeLibrary() }.collectAsState(initial = emptyList())
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<StickerEntity?>(null) }

    fun report(r: StickerImport) {
        message = when (r) {
            is StickerImport.Added -> "Added ${r.stickers.size} sticker${if (r.stickers.size > 1) "s" else ""} ✨"
            StickerImport.NoImage -> "No image on the clipboard. In your gallery, long-press the subject of a photo → Copy, then paste here."
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) scope.launch { report(c.stickers.importAll(uris)) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { scope.launch { report(c.stickers.pasteFromClipboard()) } }) {
                    Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp))
                    Text("  Paste cutout")
                }
                FilledTonalButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Filled.Image, null, Modifier.size(18.dp))
                    Text("  Pick image")
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (library.isEmpty()) {
                Text(
                    "Your sticker library is empty. Copy a cutout (long-press a person, pet or object in a photo → Copy) " +
                        "or a sticker from a chat, then tap Paste. Transparent PNGs work too.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(76.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(library, key = { it.id }) { s ->
                        Box(
                            Modifier.aspectRatio(1f).combinedClickable(
                                onClick = { onPick(s) },
                                onLongClick = { confirmDelete = s },
                            ),
                            contentAlignment = Alignment.Center,
                        ) { StickerImage(s.path, 72.dp) }
                    }
                }
                Text("Long-press a sticker to delete it.", style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 16.dp))
            }
        }
    }

    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { StickerImage(s.path, 64.dp) },
            title = { Text("Delete sticker?") },
            text = { Text("It will also be removed from every day it's stuck on.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        message = if (c.stickers.delete(s)) null else "That sticker is one of your mood faces — change the face first."
                    }
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}
