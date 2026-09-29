package com.oinky.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoodFaceTest {
    @Test fun `round trips through preferences encoding`() {
        val face = MoodFace(4, "🐷", "Happy pig", "/data/stickers/a.png")
        assertEquals(face, MoodFace.decode(4, face.encode()))
        val plain = MoodFace(2, "🌧️", "Rainy")
        assertEquals(plain, MoodFace.decode(2, plain.encode()))
    }

    @Test fun `pipes in labels cannot corrupt the format`() {
        val face = MoodFace(3, "😐", "so|so")
        assertEquals("so/so", MoodFace.decode(3, face.encode())!!.label)
    }

    @Test fun `bad input decodes to null`() {
        assertNull(MoodFace.decode(1, null))
        assertNull(MoodFace.decode(1, ""))
        assertNull(MoodFace.decode(1, "just-one-part"))
    }

    @Test fun `packs map best to worst onto scores 5 to 1`() {
        val faces = MoodPacks.all.first { it.name == "Weather" }.toFaces()
        assertEquals(listOf(5, 4, 3, 2, 1), faces.map { it.score })
        assertEquals("☀️", faces.first().emoji)
        assertEquals(Mood.entries.map { it.emoji }, MoodPacks.all.first().toFaces().map { it.emoji })
    }
}

class StickerMathTest {
    private val clear = 0x00000000
    private val solid = 0xFFFF0000.toInt()

    /** 10×8 image with an opaque 3×2 block at x=4..6, y=3..4. */
    private fun image(): IntArray = IntArray(80) { i ->
        val x = i % 10; val y = i / 10
        if (x in 4..6 && y in 3..4) solid else clear
    }

    @Test fun `trims transparent borders with padding clamped to the canvas`() {
        assertEquals(StickerMath.Box(4, 3, 7, 5), StickerMath.opaqueBounds(image(), 10, 8, pad = 0))
        assertEquals(StickerMath.Box(2, 1, 9, 7), StickerMath.opaqueBounds(image(), 10, 8, pad = 2))
        assertEquals(StickerMath.Box(0, 0, 10, 8), StickerMath.opaqueBounds(image(), 10, 8, pad = 20))
    }

    @Test fun `fully transparent image has no bounds`() {
        assertNull(StickerMath.opaqueBounds(IntArray(16) { clear }, 4, 4))
    }

    @Test fun `detects cutouts versus photos`() {
        assertTrue(StickerMath.hasTransparency(image()))
        assertFalse(StickerMath.hasTransparency(IntArray(100) { solid }))
    }

    @Test fun `fits long side without upscaling`() {
        assertEquals(1024 to 512, StickerMath.fitWithin(1024, 512, 2048))
        assertEquals(512 to 256, StickerMath.fitWithin(2048, 1024, 512))
        assertEquals(256 to 512, StickerMath.fitWithin(1000, 2000, 512))
    }
}
