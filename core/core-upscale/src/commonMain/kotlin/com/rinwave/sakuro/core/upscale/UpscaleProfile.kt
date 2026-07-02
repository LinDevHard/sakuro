package com.rinwave.sakuro.core.upscale

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Пресет обработки видео (FEATURES.md §2): абстрактная цепочка проходов,
 * которую каждый движок применяет доступными ему средствами
 * (libmpv → `.glsl`-цепочка, Media3 → цепочка `GlEffect`).
 * Несовместимые с движком проходы деградируют (пропускаются).
 */
@Serializable
data class UpscaleProfile(
    val id: String,
    val name: String,
    val description: String = "",
    val contentClass: ContentClass = ContentClass.UNKNOWN,
    val passes: List<UpscalePass> = emptyList(),
    /** Встроенные пресеты нельзя удалить/перезаписать. */
    val builtIn: Boolean = false,
) {
    val isEnabled: Boolean get() = passes.isNotEmpty()
}

@Serializable
sealed interface UpscalePass {

    /** Масштабирование к целевому разрешению: множитель к высоте источника. */
    @Serializable
    @SerialName("upscale")
    data class Upscale(val factor: Float) : UpscalePass

    /** Повышение резкости с анти-рингингом, strength 0..1. */
    @Serializable
    @SerialName("sharpen")
    data class Sharpen(val strength: Float) : UpscalePass

    /** Лёгкий edge-preserving деноиз, strength 0..1. */
    @Serializable
    @SerialName("denoise")
    data class Denoise(val strength: Float) : UpscalePass
}

fun UpscalePass.describe(): String = when (this) {
    is UpscalePass.Upscale -> "upscale ×$factor"
    is UpscalePass.Sharpen -> "sharpen $strength"
    is UpscalePass.Denoise -> "denoise $strength"
}
