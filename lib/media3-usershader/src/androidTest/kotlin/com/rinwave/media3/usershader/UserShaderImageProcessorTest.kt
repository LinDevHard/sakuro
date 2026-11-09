package com.rinwave.media3.usershader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the real GL graph on the device's GPU: still image in, still image out. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class UserShaderImageProcessorTest {

    private val copyShader = MpvUserShaderParser.parse(
        """
        //!DESC copy
        //!HOOK MAIN
        //!BIND HOOKED
        vec4 hook() { return HOOKED_tex(HOOKED_pos); }
        """.trimIndent(),
    )

    private val doubleShader = MpvUserShaderParser.parse(
        """
        //!DESC double
        //!HOOK MAIN
        //!BIND HOOKED
        //!WIDTH HOOKED.w 2 *
        //!HEIGHT HOOKED.h 2 *
        vec4 hook() { return HOOKED_tex(HOOKED_pos); }
        """.trimIndent(),
    )

    private fun fixture(name: String): ShaderDocument {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return MpvUserShaderParser.parse(assets.open("usershader/$name").bufferedReader().use { it.readText() })
    }

    /** Four solid quadrants — any flip or channel swap moves a colour to the wrong corner. */
    private fun quadrants(width: Int = 64, height: Int = 48): Bitmap {
        val pixels = IntArray(width * height) { i ->
            val left = i % width < width / 2
            val top = i / width < height / 2
            when {
                left && top -> Color.RED
                top -> Color.GREEN
                left -> Color.BLUE
                else -> Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun assertColor(expected: Int, bitmap: Bitmap, fx: Float, fy: Float, tolerance: Int = 24) {
        val actual = bitmap.getPixel((bitmap.width * fx).toInt(), (bitmap.height * fy).toInt())
        val close = abs(Color.red(actual) - Color.red(expected)) <= tolerance &&
            abs(Color.green(actual) - Color.green(expected)) <= tolerance &&
            abs(Color.blue(actual) - Color.blue(expected)) <= tolerance
        assertTrue(
            close,
            "at ($fx, $fy): expected #${Integer.toHexString(expected)}, got #${Integer.toHexString(actual)}",
        )
    }

    private fun assertQuadrants(bitmap: Bitmap, tolerance: Int = 24) {
        assertColor(Color.RED, bitmap, 0.25f, 0.25f, tolerance)
        assertColor(Color.GREEN, bitmap, 0.75f, 0.25f, tolerance)
        assertColor(Color.BLUE, bitmap, 0.25f, 0.75f, tolerance)
        assertColor(Color.WHITE, bitmap, 0.75f, 0.75f, tolerance)
    }

    @Test
    fun copyKeepsSizeOrientationAndColours() {
        UserShaderImageProcessor(copyShader).use { processor ->
            val result = processor.process(quadrants())

            assertNull(result.stats.degradedReason)
            assertEquals(64 to 48, result.bitmap.width to result.bitmap.height)
            assertQuadrants(result.bitmap, tolerance = 2)
        }
    }

    @Test
    fun upscalePassDoublesTheImage() {
        UserShaderImageProcessor(doubleShader).use { processor ->
            val result = processor.process(quadrants())

            assertEquals(128 to 96, result.bitmap.width to result.bitmap.height)
            assertQuadrants(result.bitmap)
        }
    }

    @Test
    fun realRavuUpscalesTwice() {
        UserShaderImageProcessor(fixture("ravu-r3.hook")).use { processor ->
            val result = processor.process(quadrants(128, 96))

            assertNull(result.stats.degradedReason, result.stats.degradedReason)
            assertEquals(256 to 192, result.bitmap.width to result.bitmap.height)
            assertQuadrants(result.bitmap, tolerance = 40)
        }
    }

    @Test
    fun targetSizeClosesTheUpscaleGate() {
        // ravu only runs when the output is > 1.41x the input; same-size target → skipped.
        UserShaderImageProcessor(fixture("ravu-r3.hook"), targetSize = Size(128, 96)).use { processor ->
            val result = processor.process(quadrants(128, 96))

            assertEquals(128 to 96, result.bitmap.width to result.bitmap.height)
        }
    }

    @Test
    fun theSameProcessorHandlesDifferentSizes() {
        UserShaderImageProcessor(doubleShader).use { processor ->
            assertEquals(128, processor.process(quadrants(64, 48)).bitmap.width)
            assertEquals(200, processor.process(quadrants(100, 40)).bitmap.width)
        }
    }

    @Test
    fun aClosedProcessorRefusesWork() {
        val processor = UserShaderImageProcessor(copyShader)
        processor.close()
        assertFailsWith<IllegalStateException> { processor.process(quadrants()) }
    }
}
