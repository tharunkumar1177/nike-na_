/*
 * The Media3 effect that draws a clip's chroma key: pixels near the key color turn transparent, and the key color's
 * cast is pulled out of what remains.
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
import com.framecraft.editor.domain.model.ChromaKey

/** Fragment shader of the key pass; it mirrors [ChromaKeyMath.alphaOf] and [ChromaKeyMath.despilled]. */
private val KEY_FRAGMENT_SHADER =
    """
    precision highp float;
    uniform sampler2D uTexSampler;
    uniform vec2 uKeyChroma;
    uniform float uTolerance;
    uniform float uRamp;
    uniform float uSpill;
    uniform int uDominant;
    varying vec2 vTexSamplingCoord;

    vec2 chroma(vec3 rgb) {
      return vec2(
        dot(rgb, vec3(-0.168736, -0.331264, 0.5)),
        dot(rgb, vec3(0.5, -0.418688, -0.081312)));
    }

    void main() {
      vec4 color = texture2D(uTexSampler, vTexSamplingCoord);
      float distanceToKey = distance(chroma(color.rgb), uKeyChroma);
      float kept = clamp((distanceToKey - (uTolerance - uRamp)) / uRamp, 0.0, 1.0);
      vec3 rgb = color.rgb;
      if (uDominant == 0) {
        rgb.r = mix(rgb.r, min(rgb.r, max(rgb.g, rgb.b)), uSpill);
      } else if (uDominant == 1) {
        rgb.g = mix(rgb.g, min(rgb.g, max(rgb.r, rgb.b)), uSpill);
      } else {
        rgb.b = mix(rgb.b, min(rgb.b, max(rgb.r, rgb.g)), uSpill);
      }
      gl_FragColor = vec4(rgb, color.a * kept);
    }
    """.trimIndent()

/** Keys [key] out of each frame of a clip, before its mask and look. */
@OptIn(markerClass = [UnstableApi::class])
internal class ChromaKeyEffect(
    private val key: ChromaKey,
) : GlEffect {
    /** Returns the shader program that keys the color out. */
    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean,
    ): GlShaderProgram = ChromaKeyShaderProgram(key)
}

/** The GL program of one [ChromaKeyEffect]. */
@OptIn(markerClass = [UnstableApi::class])
private class ChromaKeyShaderProgram(
    key: ChromaKey,
) : BaseGlShaderProgram(false, 1) {
    private val uniforms = ChromaKeyMath.uniformsOf(key)

    private val program =
        try {
            GlProgram(FULL_FRAME_VERTEX_SHADER, KEY_FRAGMENT_SHADER).apply {
                setBufferAttribute(
                    "aFramePosition",
                    GlUtil.getNormalizedCoordinateBounds(),
                    GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
                )
            }
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }

    /** Keeps the [inputWidth] by [inputHeight] frame size and sets the key's values. */
    override fun configure(
        inputWidth: Int,
        inputHeight: Int,
    ): Size {
        program.setFloatsUniform("uKeyChroma", uniforms.keyChroma)
        program.setFloatUniform("uTolerance", uniforms.tolerance)
        program.setFloatUniform("uRamp", uniforms.ramp)
        program.setFloatUniform("uSpill", uniforms.spill)
        program.setIntUniform("uDominant", uniforms.dominantChannel)
        return Size(inputWidth, inputHeight)
    }

    /** Draws the frame in [inputTexId] with the key applied. */
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
