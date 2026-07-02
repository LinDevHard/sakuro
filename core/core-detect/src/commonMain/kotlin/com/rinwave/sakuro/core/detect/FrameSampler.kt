package com.rinwave.sakuro.core.detect

/**
 * Уменьшенный кадр видео для анализа. Пиксели — ARGB-8888 построчно,
 * размер ~100 px по большей стороне: этого хватает статистике по цвету
 * и градиентам, а декодирование остаётся дешёвым.
 */
class FrameSample(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(argb.size == width * height) {
            "argb size ${argb.size} != $width x $height"
        }
    }
}

/**
 * Достаёт сэмплы кадров из видео независимо от движка воспроизведения
 * (Android — MediaMetadataRetriever, desktop — пока нет реализации).
 * Кадры берутся равномерно по длительности, минуя первые/последние проценты,
 * где обычно логотипы и титры.
 */
interface FrameSampler {

    /** Пустой список = сэмплирование недоступно (детекция по кадрам молчит). */
    suspend fun sample(uri: String, maxFrames: Int): List<FrameSample>
}
