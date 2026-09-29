package com.oinky.core

/**
 * How one mood score is drawn: an emoji, or a sticker image the user pasted/picked.
 * The score (1..5) never changes, so insights keep working whatever the faces look like.
 */
data class MoodFace(val score: Int, val emoji: String, val label: String, val stickerPath: String? = null) {

    /** Single-line, pipe-separated form for SharedPreferences: `emoji|label|stickerPath`. */
    fun encode(): String = listOf(emoji, label, stickerPath.orEmpty()).joinToString("|") { it.replace("|", "/") }

    companion object {
        fun decode(score: Int, raw: String?): MoodFace? {
            if (raw.isNullOrEmpty()) return null
            val p = raw.split('|')
            if (p.size < 2) return null
            return MoodFace(score, p[0], p[1], p.getOrNull(2)?.takeIf { it.isNotBlank() })
        }

        fun default(mood: Mood) = MoodFace(mood.score, mood.emoji, mood.label)
    }
}

/** Ready-made sets of five faces, best (5) to worst (1). */
object MoodPacks {
    data class Pack(val name: String, val faces: List<Pair<String, String>>) {
        init { require(faces.size == 5) }

        fun toFaces(): List<MoodFace> = faces.mapIndexed { i, (emoji, label) -> MoodFace(5 - i, emoji, label) }
    }

    val all = listOf(
        Pack("Classic", Mood.entries.map { it.emoji to it.label }),
        Pack("Piggy", listOf("🥰" to "In love", "🐷" to "Happy pig", "🐽" to "Meh", "🥲" to "Hanging on", "😭" to "Oink…")),
        Pack("Weather", listOf("☀️" to "Sunny", "🌤️" to "Bright", "☁️" to "Cloudy", "🌧️" to "Rainy", "⛈️" to "Stormy")),
        Pack("Hearts", listOf("💖" to "Glowing", "💛" to "Warm", "🤍" to "Calm", "💙" to "Blue", "🖤" to "Heavy")),
        Pack("Food", listOf("🍰" to "Treat day", "🍜" to "Cosy", "🍚" to "Plain", "🥦" to "Meh", "🧅" to "Teary")),
    )
}

/** Pixel maths for sticker cutouts, kept free of Android types so it can be unit tested. */
object StickerMath {

    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /**
     * Smallest box containing every pixel whose alpha is above [threshold], padded by [pad] px.
     * [argb] is row-major ARGB (as from `Bitmap.getPixels`). Returns null for a fully transparent image.
     * Cutouts copied from a photo keep the whole canvas; trimming makes them sit nicely on a page.
     */
    fun opaqueBounds(argb: IntArray, width: Int, height: Int, threshold: Int = 16, pad: Int = 4): Box? {
        require(argb.size == width * height)
        var minX = width; var minY = height; var maxX = -1; var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((argb[row + x] ushr 24) > threshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return null
        return Box(
            (minX - pad).coerceAtLeast(0), (minY - pad).coerceAtLeast(0),
            (maxX + 1 + pad).coerceAtMost(width), (maxY + 1 + pad).coerceAtMost(height),
        )
    }

    /** True when enough of the image is see-through that it's clearly a cutout, not a photo. */
    fun hasTransparency(argb: IntArray, minShare: Double = 0.02): Boolean {
        if (argb.isEmpty()) return false
        val clear = argb.count { (it ushr 24) < 250 }
        return clear.toDouble() / argb.size >= minShare
    }

    /** Scale (w, h) down to fit within [max] on the long side, never up. */
    fun fitWithin(width: Int, height: Int, max: Int): Pair<Int, Int> {
        val long = maxOf(width, height)
        if (long <= max) return width to height
        val f = max.toDouble() / long
        return maxOf(1, (width * f).toInt()) to maxOf(1, (height * f).toInt())
    }
}
