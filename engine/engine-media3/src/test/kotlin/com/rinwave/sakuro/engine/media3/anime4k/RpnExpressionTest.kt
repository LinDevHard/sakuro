package com.rinwave.sakuro.engine.media3.anime4k

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RpnExpressionTest {

    private val sizes = mapOf(
        "MAIN.w" to 640f, "MAIN.h" to 360f,
        "OUTPUT.w" to 1920f, "OUTPUT.h" to 1080f,
        "conv2d_last_tf.w" to 640f, "conv2d_last_tf.h" to 360f,
    )

    private fun resolve(token: String): Float = sizes[token] ?: error("no $token")

    @Test
    fun `a literal is returned as-is`() {
        assertEquals(1920f, RpnExpression.parse("1920").eval(::resolve))
    }

    @Test
    fun `the depth-to-space width is doubled`() {
        assertEquals(1280f, RpnExpression.parse("conv2d_last_tf.w 2 *").eval(::resolve))
    }

    @Test
    fun `the upscale WHEN is true when the output is larger than the input on both axes`() {
        val expr = RpnExpression.parse("OUTPUT.w MAIN.w / 1.200 > OUTPUT.h MAIN.h / 1.200 > *")
        assertTrue(expr.isTruthy(::resolve))
    }

    @Test
    fun `the upscale WHEN is false when the output is the same size`() {
        val same = mapOf("MAIN.w" to 640f, "MAIN.h" to 360f, "OUTPUT.w" to 640f, "OUTPUT.h" to 360f)
        val expr = RpnExpression.parse("OUTPUT.w MAIN.w / 1.200 > OUTPUT.h MAIN.h / 1.200 > *")
        assertFalse(expr.isTruthy { same[it] ?: error("no $it") })
    }

    @Test
    fun `subtraction and division operand order is respected`() {
        assertEquals(5f, RpnExpression.parse("10 5 -").eval { it.toFloat() })
        assertEquals(4f, RpnExpression.parse("20 5 /").eval { error("no variables") })
    }

    @Test
    fun `an empty expression does not parse`() {
        assertFailsWith<IllegalArgumentException> { RpnExpression.parse("   ") }
    }

    @Test
    fun `too few operands is an error`() {
        assertFailsWith<IllegalStateException> { RpnExpression.parse("2 *").eval { it.toFloat() } }
    }
}
