package com.rinwave.sakuro.core.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The on-disk cache for network streams, as the settings screen sees it. */
interface MediaCache {
    /** Bytes currently stored; refreshed by [refreshUsage] and after [clear]. */
    val usedBytes: StateFlow<Long>

    fun setLimitBytes(bytes: Long)

    fun refreshUsage()

    /** Drops everything that is not being read right now. */
    fun clear()

    companion object {
        const val MB: Long = 1024L * 1024L

        /** Choices offered in settings; 0 disables caching. */
        val LIMIT_CHOICES_MB: List<Int> = listOf(0, 256, 512, 1024, 2048, 4096)
        const val DEFAULT_LIMIT_MB: Int = 1024
    }
}

/** For targets without network playback (desktop sandbox). */
class NoMediaCache : MediaCache {
    private val _usedBytes = MutableStateFlow(0L)
    override val usedBytes: StateFlow<Long> = _usedBytes.asStateFlow()
    override fun setLimitBytes(bytes: Long) = Unit
    override fun refreshUsage() = Unit
    override fun clear() = Unit
}
