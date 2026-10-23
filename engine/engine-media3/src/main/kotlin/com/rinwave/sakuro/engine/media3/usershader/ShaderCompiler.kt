package com.rinwave.sakuro.engine.media3.usershader

import android.opengl.GLES20

/** Driver boundary for compilation; ownership and failure cleanup stay in [compileShaderProgram]. */
internal interface ShaderCompilerApi {
    fun createProgram(): Int
    fun createShader(type: Int): Int
    fun compileShader(shader: Int, source: String)
    fun attachShader(program: Int, shader: Int)
    fun linkProgram(program: Int)
    fun deleteShader(shader: Int)
    fun deleteProgram(program: Int)
}

/** Returns a linked program owned by the caller; every failed construction cleans itself up. */
internal fun compileShaderProgram(
    stages: List<Pair<Int, String>>,
    driver: ShaderCompilerApi = GlesShaderCompiler,
): Int {
    val program = driver.createProgram()
    var linked = false
    try {
        stages.forEach { (type, source) ->
            val shader = driver.createShader(type)
            try {
                driver.compileShader(shader, source)
                driver.attachShader(program, shader)
            } finally {
                driver.deleteShader(shader)
            }
        }
        driver.linkProgram(program)
        linked = true
        return program
    } finally {
        if (!linked) driver.deleteProgram(program)
    }
}

private object GlesShaderCompiler : ShaderCompilerApi {
    override fun createProgram(): Int = GLES20.glCreateProgram().also {
        if (it == 0) throw UserShaderException("could not allocate GL program")
    }

    override fun createShader(type: Int): Int = GLES20.glCreateShader(type).also {
        if (it == 0) throw UserShaderException("could not allocate GL shader")
    }

    override fun compileShader(shader: Int, source: String) {
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) throw UserShaderException("shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
    }

    override fun attachShader(program: Int, shader: Int) = GLES20.glAttachShader(program, shader)

    override fun linkProgram(program: Int) {
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) throw UserShaderException("program link failed: ${GLES20.glGetProgramInfoLog(program)}")
    }

    override fun deleteShader(shader: Int) = GLES20.glDeleteShader(shader)
    override fun deleteProgram(program: Int) = GLES20.glDeleteProgram(program)
}
