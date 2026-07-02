package com.rinwave.sakuro.ui

/**
 * Режим кадра (FEATURES.md §3.1): fit — вписать целиком, fill — растянуть
 * на весь экран, zoom — заполнить экран с обрезкой краёв (crop).
 */
enum class ScaleMode {
    FIT,
    FILL,
    ZOOM,
}

val ScaleMode.displayName: String
    get() = when (this) {
        ScaleMode.FIT -> "Вписать"
        ScaleMode.FILL -> "Растянуть"
        ScaleMode.ZOOM -> "Заполнить"
    }
