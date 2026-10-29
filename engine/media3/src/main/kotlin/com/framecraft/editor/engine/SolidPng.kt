/*
 * Encodes one-pixel PNG images as data URIs, so a still of any color can be played without writing a file.
 */
package com.framecraft.editor.engine

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater

/** The eight bytes every PNG file starts with. */
private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

/** PNG bit depth of eight bits per channel. */
private const val BIT_DEPTH = 8

/** PNG color type for red, green, blue, and alpha. */
private const val COLOR_TYPE_RGBA = 6

/** Bit offset of the alpha channel in an ARGB color. */
private const val ALPHA_SHIFT = 24

/** Bit offset of the red channel in an ARGB color. */
private const val RED_SHIFT = 16

/** Bit offset of the green channel in an ARGB color. */
private const val GREEN_SHIFT = 8

/** Mask of one eight-bit channel. */
private const val CHANNEL_MASK = 0xFF

/** Size of the buffer compressed bytes are read through. */
private const val DEFLATE_BUFFER_BYTES = 64

/** Creates one-pixel PNG data URIs. */
internal object SolidPng {
    /** Returns a `data:` URI of a one-pixel PNG of [argb], packed as alpha, red, green, blue. */
    fun dataUri(argb: Int): String = "data:image/png;base64," + Base64.getEncoder().encodeToString(encode(argb))

    /** Returns the bytes of a one-pixel RGBA PNG of [argb]. */
    fun encode(argb: Int): ByteArray {
        val header =
            ByteArrayOutputStream().also { bytes ->
                DataOutputStream(bytes).apply {
                    writeInt(1)
                    writeInt(1)
                    writeByte(BIT_DEPTH)
                    writeByte(COLOR_TYPE_RGBA)
                    writeByte(0)
                    writeByte(0)
                    writeByte(0)
                }
            }
        val row =
            byteArrayOf(
                0,
                channel(argb, RED_SHIFT),
                channel(argb, GREEN_SHIFT),
                channel(argb, 0),
                channel(argb, ALPHA_SHIFT),
            )
        val out = ByteArrayOutputStream()
        out.write(PNG_SIGNATURE)
        writeChunk(out, "IHDR", header.toByteArray())
        writeChunk(out, "IDAT", deflate(row))
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    /** Returns the eight-bit channel of [argb] at [shift]. */
    private fun channel(
        argb: Int,
        shift: Int,
    ): Byte = ((argb ushr shift) and CHANNEL_MASK).toByte()

    /** Returns [data] compressed as a zlib stream. */
    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFLATE_BUFFER_BYTES)
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()
        return out.toByteArray()
    }

    /** Writes chunk [type] holding [data] to [out] with its length and checksum. */
    private fun writeChunk(
        out: ByteArrayOutputStream,
        type: String,
        data: ByteArray,
    ) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc =
            CRC32().apply {
                update(typeBytes)
                update(data)
            }
        DataOutputStream(out).apply {
            writeInt(data.size)
            write(typeBytes)
            write(data)
            writeInt(crc.value.toInt())
        }
    }
}
