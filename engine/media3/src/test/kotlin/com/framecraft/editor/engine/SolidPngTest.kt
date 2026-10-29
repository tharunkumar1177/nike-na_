/*
 * Unit tests for one-pixel PNG data URIs.
 */
package com.framecraft.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

/** Verifies that [SolidPng] writes PNGs a standard decoder reads back as the requested color. */
class SolidPngTest {
    /** Decodes the one pixel of [uri] as ARGB. */
    private fun pixelOf(uri: String): Int {
        val bytes = Base64.getDecoder().decode(uri.substringAfter("base64,"))
        val image = requireNotNull(ImageIO.read(ByteArrayInputStream(bytes)))
        assertEquals(1, image.width)
        assertEquals(1, image.height)
        return image.getRGB(0, 0)
    }

    /** Opaque and transparent colors decode to exactly the requested channels. */
    @Test
    fun colorsDecodeExactly() {
        val orange = 0xFFFF8800.toInt()
        val uri = SolidPng.dataUri(orange)
        assertTrue(uri.startsWith("data:image/png;base64,"))
        assertEquals(orange, pixelOf(uri))
        assertEquals(0x80123456.toInt(), pixelOf(SolidPng.dataUri(0x80123456.toInt())))
        assertEquals(0, pixelOf(SolidPng.dataUri(0)) ushr 24)
    }
}
