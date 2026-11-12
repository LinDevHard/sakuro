package com.rinwave.sakuro.core.model

/** What a GPU probe found on this device; engines and reports read it, nothing renders with it. */
data class GpuInfo(
    val renderer: String,
    val vendor: String,
    val glVersion: String,
    val glesMajor: Int,
    val glesMinor: Int,
    val fp16Renderable: Boolean,
    val compute: Boolean,
)

/** Supplies [GpuInfo]; the app wires it to the product engine's probe. */
fun interface GpuInfoProvider {
    /** Null when the probe failed or the platform has no GPU details. */
    fun gpuInfo(): GpuInfo?
}
