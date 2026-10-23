package com.rinwave.sakuro.engine.media3.usershader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResourceScopeTest {
    @Test
    fun `partial initialization releases acquired resources even if cleanup fails`() {
        val scope = ResourceScope()
        val live = mutableSetOf(1, 2)
        assertFailsWith<IllegalStateException> {
            try {
                scope.own(1) { live.remove(it) }
                scope.own(2) {
                    live.remove(it)
                    error("driver cleanup failure")
                }
                error("third allocation failed")
            } finally {
                scope.release()
            }
        }
        assertEquals(emptySet(), live)
    }

    @Test
    fun `release is idempotent and reusable for reconfiguration`() {
        val scope = ResourceScope()
        val released = mutableListOf<Int>()
        scope.own(1) { released += it }
        scope.release()
        scope.release()
        scope.own(2) { released += it }
        scope.release()
        assertEquals(listOf(1, 2), released)
    }
}
