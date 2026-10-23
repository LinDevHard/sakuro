package com.rinwave.sakuro.engine.media3.usershader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShaderCompilerTest {
    @Test
    fun `compile failure releases failing shader and partially built program`() {
        val driver = FakeCompiler(failCompile = true)
        assertFailsWith<UserShaderException> { compileShaderProgram(listOf(1 to "vertex", 2 to "fragment"), driver) }
        assertEquals(emptySet(), driver.liveShaders)
        assertEquals(emptySet(), driver.livePrograms)
    }

    @Test
    fun `link failure releases program and all shader objects`() {
        val driver = FakeCompiler(failLink = true)
        assertFailsWith<UserShaderException> { compileShaderProgram(listOf(1 to "vertex", 2 to "fragment"), driver) }
        assertEquals(emptySet(), driver.liveShaders)
        assertEquals(emptySet(), driver.livePrograms)
    }

    @Test
    fun `successful compilation transfers only the linked program to its owner`() {
        val driver = FakeCompiler()
        val owner = ResourceScope()
        owner.own(compileShaderProgram(listOf(1 to "vertex", 2 to "fragment"), driver), driver::deleteProgram)
        assertEquals(emptySet(), driver.liveShaders)
        assertEquals(setOf(10), driver.livePrograms)
        owner.release()
        assertEquals(emptySet(), driver.livePrograms)
    }

    private class FakeCompiler(
        val failCompile: Boolean = false,
        val failLink: Boolean = false,
    ) : ShaderCompilerApi {
        val liveShaders = mutableSetOf<Int>()
        val livePrograms = mutableSetOf<Int>()
        override fun createProgram(): Int = 10.also { livePrograms += it }
        override fun createShader(type: Int): Int = type.also { liveShaders += it }
        override fun compileShader(shader: Int, source: String) {
            if (failCompile && shader == 2) throw UserShaderException("invalid fragment")
        }
        override fun attachShader(program: Int, shader: Int) = Unit
        override fun linkProgram(program: Int) {
            if (failLink) throw UserShaderException("incompatible stages")
        }
        override fun deleteShader(shader: Int) { liveShaders.remove(shader) }
        override fun deleteProgram(program: Int) { livePrograms.remove(program) }
    }
}
