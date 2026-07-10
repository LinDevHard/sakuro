package com.rinwave.sakuro.engine.media3.usershader

/**
 * Deterministic binding-point assignment for `//!BUFFER` blocks and
 * `//!TEXTURE … STORAGE` images. [ShaderPreamble] emits these indices in the
 * layout qualifiers and [UserShaderProgram] binds the GL objects to the same
 * points, so both sides derive them from the document alone.
 */
internal object ShaderBindings {

    /** `out_image` of a compute pass always occupies image unit 0. */
    const val OUT_IMAGE_UNIT = 0

    /** SSBO binding point of a `//!STORAGE` buffer (indexed among storage buffers). */
    fun ssboBinding(document: ShaderDocument, name: String): Int =
        document.buffers.filter { it.storage }.indexOfFirst { it.name == name }

    /** UBO binding point of a plain `//!BUFFER` (indexed among uniform buffers). */
    fun uboBinding(document: ShaderDocument, name: String): Int =
        document.buffers.filterNot { it.storage }.indexOfFirst { it.name == name }

    /** Image unit of a storage texture; unit 0 is reserved for `out_image`. */
    fun imageUnit(document: ShaderDocument, name: String): Int =
        1 + document.textures.filter { it.storage }.indexOfFirst { it.name == name }

    /** How a pass body accesses a storage image (drives the ES 3.1 memory qualifier). */
    enum class ImageAccess { READ, WRITE, READ_WRITE }

    fun imageAccess(body: String, name: String): ImageAccess {
        val writes = Regex("""imageStore\s*\(\s*$name\b""").containsMatchIn(body)
        val reads = Regex("""imageLoad\s*\(\s*$name\b""").containsMatchIn(body)
        return when {
            writes && reads -> ImageAccess.READ_WRITE
            writes -> ImageAccess.WRITE
            else -> ImageAccess.READ
        }
    }
}
