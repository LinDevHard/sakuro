package com.rinwave.sakuro.engine.media3.usershader

import android.opengl.GLES20
import androidx.media3.common.util.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Fragment program with explicit construction ownership. Media3's GlProgram constructor
 * does not release its program/shader IDs on compilation failure, so imported GLSL uses
 * our exception-safe compiler. This wrapper binds the vertex attribute and uniforms used
 * by the user-shader preamble and presenter; [owner] owns the linked program immediately.
 */
internal class FragmentProgram(vertex: String, fragment: String, owner: ResourceScope) {
    private val id = owner.own(
        compileShaderProgram(listOf(GLES20.GL_VERTEX_SHADER to vertex, GLES20.GL_FRAGMENT_SHADER to fragment)),
    ) { GLES20.glDeleteProgram(it) }

    private data class Attribute(val location: Int, val buffer: FloatBuffer, val size: Int)

    private val attributes = mutableMapOf<String, Attribute>()
    private val uniforms = mutableMapOf<String, () -> Unit>()
    private val locations = mutableMapOf<String, Int>()

    fun use() {
        GLES20.glUseProgram(id)
        GlUtil.checkGlError()
    }

    fun setBufferAttribute(name: String, values: FloatArray, size: Int) {
        val buffer = ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(values).position(0)
        attributes[name] = Attribute(GLES20.glGetAttribLocation(id, name), buffer, size)
    }

    fun setSamplerTexIdUniform(name: String, texture: Int, unit: Int) {
        val location = location(name)
        uniforms[name] = {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glUniform1i(location, unit)
        }
    }

    fun setIntUniform(name: String, value: Int) {
        val location = location(name)
        uniforms[name] = { GLES20.glUniform1i(location, value) }
    }

    fun setFloatUniform(name: String, value: Float) {
        val location = location(name)
        uniforms[name] = { GLES20.glUniform1f(location, value) }
    }

    fun setFloatsUniform(name: String, values: FloatArray) {
        val location = location(name)
        // The preamble and presenter expose only vec2 float uniforms.
        require(values.size == 2) { "expected vec2 uniform '$name'" }
        uniforms[name] = { GLES20.glUniform2fv(location, 1, values, 0) }
    }

    fun bindAttributesAndUniforms() {
        // Media3 shares this context: FloatBuffer attributes require client-memory mode.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        attributes.values.forEach { attribute ->
            if (attribute.location >= 0) {
                GLES20.glEnableVertexAttribArray(attribute.location)
                GLES20.glVertexAttribPointer(
                    attribute.location, attribute.size, GLES20.GL_FLOAT, false, 0, attribute.buffer,
                )
            }
        }
        uniforms.values.forEach { it() }
        GlUtil.checkGlError()
    }

    private fun location(name: String): Int = locations.getOrPut(name) { GLES20.glGetUniformLocation(id, name) }
}
