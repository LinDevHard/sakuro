package com.rinwave.sakuro.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayModePickerTest {

    private fun mode(id: Int, hz: Float, w: Int = 3840, h: Int = 2160) = DisplayModeInfo(id, w, h, hz)

    private val tv =
        listOf(mode(1, 60f), mode(2, 50f), mode(3, 24f), mode(4, 23.976f), mode(5, 30f), mode(6, 60f, 1920, 1080))
    private val phone = listOf(mode(1, 60f, 1080, 2400), mode(2, 90f, 1080, 2400), mode(3, 120f, 1080, 2400))

    @Test
    fun `film frame rates pick the exact tv mode`() {
        assertEquals(4, DisplayModePicker.pick(tv, tv[0], 23.976f)?.id)
        assertEquals(3, DisplayModePicker.pick(tv, tv[0], 24f)?.id)
        assertEquals(2, DisplayModePicker.pick(tv, tv[0], 25f)?.id)
    }

    @Test
    fun `phones use a whole multiple`() {
        assertEquals(3, DisplayModePicker.pick(phone, phone[0], 24f)?.id)
        assertEquals(1, DisplayModePicker.pick(phone, phone[0], 30f)?.id)
    }

    @Test
    fun `no matching mode or unknown rate keeps the current mode`() {
        assertNull(DisplayModePicker.pick(phone, phone[0], 25f))
        assertNull(DisplayModePicker.pick(tv, tv[0], 0f))
    }

    @Test
    fun `other resolutions are never chosen`() {
        assertEquals(1, DisplayModePicker.pick(tv, tv[0], 60f)?.id)
    }
}
