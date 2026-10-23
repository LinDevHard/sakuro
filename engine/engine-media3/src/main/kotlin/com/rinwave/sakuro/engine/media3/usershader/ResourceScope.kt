package com.rinwave.sakuro.engine.media3.usershader

/** Registers ownership before fallible initialization, including partially built graphs. */
internal class ResourceScope {
    private val releases = mutableListOf<() -> Unit>()

    fun <T> own(resource: T, release: (T) -> Unit): T {
        releases += { release(resource) }
        return resource
    }

    /** Every resource gets a release attempt, even when another cleanup fails. */
    fun release() {
        val pending = releases.toList()
        releases.clear()
        pending.asReversed().forEach { runCatching { it() } }
    }
}
