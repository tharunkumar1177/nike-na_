/*
 * Unit tests for the CPU mirror of the mask shader, the traced mask outlines, and the order of a segment's effects.
 */
package com.framecraft.editor.engine

import com.framecraft.editor.domain.model.ChromaKey
import com.framecraft.editor.domain.model.ClipLook
import com.framecraft.editor.domain.model.ClipMask
import com.framecraft.editor.domain.model.MaskShape
import com.framecraft.editor.domain.model.MediaKind
import com.framecraft.editor.engine.plan.PlanSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** Tolerance for comparing alpha and distances. */
private const val DELTA = 1e-3f

/** Verifies mask math on a square picture unless a test says otherwise. */
class MaskMathTest {
    /** Returns the visibility of [mask] at [x], [y] on a picture [aspect] wide. */
    private fun alpha(
        mask: ClipMask,
        x: Float,
        y: Float,
        aspect: Float = 1f,
    ) = MaskMath.alphaAt(mask, x, y, aspect)

    /** A hard circle shows its inside and hides its outside; inverting swaps them. */
    @Test
    fun circleShowsInside() {
        val circle = ClipMask(MaskShape.CIRCLE)
        assertEquals(1f, alpha(circle, 0f, 0f), DELTA)
        assertEquals(1f, alpha(circle, 0.4f, 0f), DELTA)
        assertEquals(0f, alpha(circle, 0.6f, 0f), DELTA)
        assertEquals(0.5f, alpha(circle, 0.5f, 0f), DELTA)
        val inverted = circle.copy(invert = true)
        assertEquals(0f, alpha(inverted, 0f, 0f), DELTA)
        assertEquals(1f, alpha(inverted, 0.9f, 0.9f), DELTA)
    }

    /** On a wide picture the circle stays round: it reaches less far in picture x than in picture y. */
    @Test
    fun circleStaysRoundOnWidePictures() {
        val circle = ClipMask(MaskShape.CIRCLE)
        assertEquals(1f, alpha(circle, 0f, 0.45f, aspect = 2f), DELTA)
        assertEquals(0f, alpha(circle, 0.3f, 0f, aspect = 2f), DELTA)
        assertEquals(1f, alpha(circle, 0.2f, 0f, aspect = 2f), DELTA)
    }

    /** The linear edge keeps the left half upright, and a quarter turn clockwise keeps the top half. */
    @Test
    fun linearKeepsLeftThenTurns() {
        val linear = ClipMask(MaskShape.LINEAR)
        assertEquals(1f, alpha(linear, -0.5f, 0f), DELTA)
        assertEquals(0f, alpha(linear, 0.5f, 0f), DELTA)
        val turned = linear.copy(rotationDegrees = 90f)
        assertEquals(1f, alpha(turned, 0f, -0.5f), DELTA)
        assertEquals(0f, alpha(turned, 0f, 0.5f), DELTA)
    }

    /** A mirror band keeps a vertical strip as wide as twice its half-width. */
    @Test
    fun mirrorKeepsBand() {
        val band = ClipMask(MaskShape.MIRROR, halfWidth = 0.25f)
        assertEquals(1f, alpha(band, 0.2f, 0.9f), DELTA)
        assertEquals(0f, alpha(band, 0.3f, 0f), DELTA)
        assertEquals(0f, alpha(band, -0.3f, 0f), DELTA)
    }

    /** A rectangle with rounded corners hides its corner tips but keeps its edges; it follows its center. */
    @Test
    fun rectangleRoundsCornersAndMoves() {
        val square = ClipMask(MaskShape.RECTANGLE, halfWidth = 0.5f, halfHeight = 0.5f)
        assertEquals(1f, alpha(square, 0.48f, 0.48f), DELTA)
        val rounded = square.copy(cornerPercent = 100)
        assertEquals(0f, alpha(rounded, 0.48f, 0.48f), DELTA)
        assertEquals(1f, alpha(rounded, 0.48f, 0f), DELTA)
        val moved = square.copy(centerX = 0.5f)
        assertEquals(0f, alpha(moved, -0.2f, 0f), DELTA)
        assertEquals(1f, alpha(moved, 0.9f, 0f), DELTA)
    }

    /** Heart and star keep their middles, lose far corners, and the heart's point sits at the bottom. */
    @Test
    fun heartAndStarShapes() {
        val heart = ClipMask(MaskShape.HEART, halfWidth = 0.6f, halfHeight = 0.6f)
        assertEquals(1f, alpha(heart, 0f, 0f), DELTA)
        assertEquals(0f, alpha(heart, 0.55f, 0.55f), DELTA)
        assertEquals(1f, alpha(heart, 0f, 0.5f), DELTA)
        val star = ClipMask(MaskShape.STAR, halfWidth = 0.6f, halfHeight = 0.6f)
        assertEquals(1f, alpha(star, 0f, 0f), DELTA)
        assertEquals(1f, alpha(star, 0f, -0.55f), DELTA)
        assertEquals(0f, alpha(star, 0f, 0.55f), DELTA)
    }

    /** Feather widens the soft edge: halfway through it a point is partly shown. */
    @Test
    fun featherSoftensEdge() {
        val soft = ClipMask(MaskShape.CIRCLE, featherPercent = 100)
        val inner = alpha(soft, 0.4f, 0f)
        assertTrue("inner $inner", inner > 0.5f && inner < 1f)
        assertEquals(MAX_FEATHER_WIDTH, MaskMath.featherWidthOf(100), DELTA)
        assertEquals(MIN_FEATHER_WIDTH, MaskMath.featherWidthOf(0), DELTA)
    }

    /** The uniforms carry the shape index, turn, and invert flag the shader reads. */
    @Test
    fun uniformsMatchMask() {
        val uniforms = MaskMath.uniformsOf(ClipMask(MaskShape.STAR, rotationDegrees = 90f, invert = true))
        assertEquals(MaskShape.STAR.ordinal, uniforms.shapeIndex)
        assertEquals(0f, uniforms.turn[0], DELTA)
        assertEquals(1f, uniforms.turn[1], DELTA)
        assertEquals(1f, uniforms.invert, DELTA)
    }

    /** A circle's outline lies on its edge; edges and bands have none. */
    @Test
    fun outlinesTraceTheEdge() {
        val circle = ClipMask(MaskShape.CIRCLE, halfWidth = 0.4f, halfHeight = 0.4f)
        val points = MaskOutline.closedOutlineOf(circle)
        assertEquals(72, points.size)
        points.forEach { assertEquals(0.4f, hypot(it.x, it.y), DELTA) }
        MaskShape.entries.filter { it != MaskShape.LINEAR && it != MaskShape.MIRROR }.forEach { shape ->
            val mask = ClipMask.defaultFor(shape)
            MaskOutline.closedOutlineOf(mask).forEach { point ->
                assertEquals(0f, MaskOutline.distanceOf(mask, point.x, point.y), DELTA)
            }
        }
        assertTrue(MaskOutline.closedOutlineOf(ClipMask.defaultFor(MaskShape.LINEAR)).isEmpty())
        assertTrue(MaskOutline.closedOutlineOf(ClipMask.defaultFor(MaskShape.MIRROR)).isEmpty())
    }

    /** A segment's effects run key, then mask, then look, and a plain segment has none. */
    @Test
    fun effectsRunKeyMaskLook() {
        val plain = PlanSegment.Media("content://v", MediaKind.VIDEO, 1_000_000L, 0L, 1_000_000L)
        assertTrue(SegmentVideoEffects.of(plain).isEmpty())
        val dressed =
            plain.copy(
                mask = ClipMask(MaskShape.CIRCLE),
                chromaKey = ChromaKey(0xFF00FF00.toInt()),
                look = ClipLook(saturation = 20),
            )
        val effects = SegmentVideoEffects.of(dressed)
        assertEquals(3, effects.size)
        assertTrue(effects[0] is ChromaKeyEffect)
        assertTrue(effects[1] is ClipMaskEffect)
    }
}
