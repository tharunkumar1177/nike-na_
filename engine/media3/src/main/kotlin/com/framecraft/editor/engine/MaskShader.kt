/*
 * The Media3 effect that draws a clip's shape mask: it keeps the picture's colors and writes how visible each pixel
 * stays into its alpha, which the compositor blends over what lies beneath.
 */
package com.framecraft.editor.engine

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.framecraft.editor.domain.model.ClipMask

/** Vertex shader shared by the mask and key passes: a full-frame quad with texture coordinates. */
internal val FULL_FRAME_VERTEX_SHADER =
    """
    attribute vec4 aFramePosition;
    varying vec2 vTexSamplingCoord;
    void main() {
      gl_Position = aFramePosition;
      vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
    }
    """.trimIndent()

/** Fragment shader of the mask pass; it mirrors [MaskMath.alphaAt]. */
private val MASK_FRAGMENT_SHADER =
    """
    precision highp float;
    uniform sampler2D uTexSampler;
    uniform float uAspect;
    uniform vec2 uCenter;
    uniform vec2 uHalfSize;
    uniform vec2 uTurn;
    uniform float uFeather;
    uniform float uCorner;
    uniform float uInvert;
    uniform int uShape;
    varying vec2 vTexSamplingCoord;

    float roundBox(vec2 q, vec2 b, float r) {
      vec2 d = abs(q) - b + vec2(r);
      return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
    }

    float heart(vec2 p) {
      p.x = abs(p.x);
      if (p.y + p.x > 1.0) {
        return length(p - vec2(0.25, 0.75)) - 0.35355339;
      }
      vec2 top = p - vec2(0.0, 1.0);
      vec2 fold = p - vec2(0.5 * max(p.x + p.y, 0.0));
      return sqrt(min(dot(top, top), dot(fold, fold))) * sign(p.x - p.y);
    }

    float star(vec2 p) {
      vec2 k1 = vec2(0.809017, -0.58778524);
      vec2 k2 = vec2(-k1.x, k1.y);
      p.x = abs(p.x);
      p -= 2.0 * max(dot(k1, p), 0.0) * k1;
      p -= 2.0 * max(dot(k2, p), 0.0) * k2;
      p.x = abs(p.x);
      p.y -= 1.0;
      vec2 ba = 0.5 * vec2(-k1.y, k1.x) - vec2(0.0, 1.0);
      float h = clamp(dot(p, ba) / dot(ba, ba), 0.0, 1.0);
      return length(p - ba * h) * sign(p.y * ba.x - p.x * ba.y);
    }

    float shapeDistance(vec2 q) {
      float least = min(uHalfSize.x, uHalfSize.y);
      vec2 n = q / uHalfSize;
      if (uShape == 0) {
        return q.x;
      } else if (uShape == 1) {
        return abs(q.x) - uHalfSize.x;
      } else if (uShape == 2) {
        return (length(n) - 1.0) * least;
      } else if (uShape == 3) {
        return roundBox(q, uHalfSize, uCorner * least);
      } else if (uShape == 4) {
        return heart(vec2(n.x * 0.6036, 0.5518 * (1.0 - n.y))) * least / 0.5518;
      }
      return star(vec2(n.x, -n.y)) * least;
    }

    void main() {
      vec4 color = texture2D(uTexSampler, vTexSamplingCoord);
      vec2 p = vec2(vTexSamplingCoord.x * 2.0 - 1.0, 1.0 - vTexSamplingCoord.y * 2.0) - uCenter;
      p.x *= uAspect;
      vec2 q = vec2(uTurn.x * p.x + uTurn.y * p.y, -uTurn.y * p.x + uTurn.x * p.y);
      float shown = 1.0 - smoothstep(-uFeather, uFeather, shapeDistance(q));
      shown = mix(shown, 1.0 - shown, uInvert);
      gl_FragColor = vec4(color.rgb, color.a * shown);
    }
    """.trimIndent()

/** Draws [mask] on each frame of a clip, in the clip's own picture before it is placed on the canvas. */
@OptIn(markerClass = [UnstableApi::class])
internal class ClipMaskEffect(
    private val mask: ClipMask,
) : GlEffect {
    /** Returns the shader program that draws the mask. */
    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean,
    ): GlShaderProgram = MaskShaderProgram(mask)
}

/** The GL program of one [ClipMaskEffect]. */
@OptIn(markerClass = [UnstableApi::class])
private class MaskShaderProgram(
    mask: ClipMask,
) : BaseGlShaderProgram(false, 1) {
    private val uniforms = MaskMath.uniformsOf(mask)

    private val program =
        try {
            GlProgram(FULL_FRAME_VERTEX_SHADER, MASK_FRAGMENT_SHADER).apply {
                setBufferAttribute(
                    "aFramePosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
                )
            }
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }

    /** Keeps the frame size and sets the mask's values, with the picture's shape from [inputWidth] by [inputHeight]. */
    override fun configure(
        inputWidth: Int,
        inputHeight: Int,
    ): Size {
        program.setFloatUniform("uAspect", inputWidth.toFloat() / inputHeight)
        program.setFloatsUniform("uCenter", uniforms.center)
        program.setFloatsUniform("uHalfSize", uniforms.halfSize)
        program.setFloatsUniform("uTurn", uniforms.turn)
        program.setFloatUniform("uFeather", uniforms.featherWidth)
        program.setFloatUniform("uCorner", uniforms.cornerFraction)
        program.setFloatUniform("uInvert", uniforms.invert)
        program.setIntUniform("uShape", uniforms.shapeIndex)
        return Size(inputWidth, inputHeight)
    }

    /** Draws the frame in [inputTexId] with the mask applied. */
    override fun drawFrame(
        inputTexId: Int,
        presentationTimeUs: Long,
    ) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, QUAD_VERTICES)
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error, presentationTimeUs)
        }
    }

    /** Releases the frame pool and the program. */
    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }
}

/** Vertices of the full-frame triangle strip. */
internal const val QUAD_VERTICES = 4
