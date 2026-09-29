package com.oinky.app.data

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.oinky.app.widget.WidgetRefresher
import com.oinky.core.StickerMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.random.Random

sealed interface StickerImport {
    data class Added(val stickers: List<StickerEntity>) : StickerImport
    /** The clipboard (or picked file) held no usable image. */
    data object NoImage : StickerImport
}

/**
 * The sticker library. Stickers come from the clipboard (a subject "cutout" copied from the
 * Photos/Gallery app, or a sticker copied from a chat) or from a picked image. Each is scaled
 * down, trimmed to its visible pixels when it has transparency, and stored as a PNG.
 */
class StickerRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: Settings,
) {
    fun observeLibrary(): Flow<List<StickerEntity>> = db.stickers().observeLibrary()
    fun observeDay(day: Long): Flow<List<DaySticker>> = db.stickers().observeDayStickers(day, day)
    fun observeRange(from: Long, to: Long): Flow<List<DaySticker>> = db.stickers().observeDayStickers(from, to)

    /** Reads every image on the clipboard into the library. Call only from a user action. */
    suspend fun pasteFromClipboard(): StickerImport {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = clipboard.primaryClip ?: return StickerImport.NoImage
        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
            .filter { uri -> context.contentResolver.getType(uri)?.startsWith("image/") ?: clip.description.hasMimeType("image/*") }
        return importAll(uris)
    }

    suspend fun importAll(uris: List<Uri>): StickerImport {
        val added = uris.mapNotNull { runCatching { import(it) }.getOrNull() }
        return if (added.isEmpty()) StickerImport.NoImage else StickerImport.Added(added)
    }

    private suspend fun import(uri: Uri): StickerEntity = withContext(Dispatchers.IO) {
        val bitmap = decode(uri, MAX_SIDE)
        val trimmed = trim(bitmap)
        val dir = File(context.filesDir, "stickers").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.png")
        file.outputStream().use { trimmed.compress(Bitmap.CompressFormat.PNG, 100, it) }
        StickerEntity(path = file.absolutePath).let { it.copy(id = db.stickers().insert(it)) }
    }

    /** Decodes to a software ARGB bitmap no larger than [maxSide] on its long edge, keeping alpha. */
    private fun decode(uri: Uri, maxSide: Int): Bitmap {
        val raw = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // getPixels() needs a software bitmap
                val (w, h) = StickerMath.fitWithin(info.size.width, info.size.height, maxSide)
                decoder.setTargetSize(w, h)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: error("Not an image")
        }
        val (w, h) = StickerMath.fitWithin(raw.width, raw.height, maxSide)
        val sized = if (w != raw.width || h != raw.height) Bitmap.createScaledBitmap(raw, w, h, true) else raw
        return if (sized.config == Bitmap.Config.ARGB_8888) sized else sized.copy(Bitmap.Config.ARGB_8888, false)
    }

    /** Crops away empty transparent margins so cutouts sit snugly on the page. */
    private fun trim(bitmap: Bitmap): Bitmap {
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        if (!StickerMath.hasTransparency(px)) return bitmap
        val box = StickerMath.opaqueBounds(px, bitmap.width, bitmap.height) ?: return bitmap
        return Bitmap.createBitmap(bitmap, box.left, box.top, box.width, box.height)
    }

    suspend fun stick(day: Long, sticker: StickerEntity) {
        // A small random tilt so a page of stickers looks hand-placed.
        db.stickers().stick(DayStickerEntity(epochDay = day, stickerId = sticker.id, rotation = Random.nextInt(-10, 11).toFloat()))
        WidgetRefresher.request(context)
    }

    suspend fun unstick(daySticker: DaySticker) = db.stickers().unstick(daySticker.id)

    /** Removes a sticker from the library and every day it was stuck on. Refused while it's a mood face. */
    suspend fun delete(sticker: StickerEntity): Boolean {
        if (settings.moodFaces.value.any { it.stickerPath == sticker.path }) return false
        db.stickers().unstickEverywhere(sticker.id)
        db.stickers().delete(sticker)
        withContext(Dispatchers.IO) { File(sticker.path).delete() }
        return true
    }

    private companion object {
        const val MAX_SIDE = 512
    }
}
