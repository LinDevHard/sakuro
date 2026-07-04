package com.rinwave.sakuro.engine.media3.anime4k

/**
 * Один проход mpv user-shader — тело `hook()` плюс распарсенные директивы
 * `//!HOOK/BIND/SAVE/WIDTH/HEIGHT/COMPONENTS/WHEN` (см. [MpvUserShaderParser]).
 *
 * Имена [hook]/[save] могут быть «стадиями» пайплайна (`MAIN`, `PREKERNEL`,
 * `HOOKED`) или именованными промежутками (`conv2d_tf`); runtime резолвит их
 * в конкретные текстуры при исполнении графа.
 */
internal data class UserShaderPass(
    val desc: String,
    /** Стадия, которую хукаем; `HOOKED` в теле ссылается на её текстуру. */
    val hook: String,
    /** Входные текстуры (в порядке `//!BIND`); может включать `HOOKED`/`MAIN`. */
    val binds: List<String>,
    /** Куда пишем результат; по умолчанию — хукнутая стадия (in-place). */
    val save: String,
    /** RPN-формула ширины выхода; null → ширина хукнутой стадии. */
    val width: RpnExpression?,
    /** RPN-формула высоты выхода; null → высота хукнутой стадии. */
    val height: RpnExpression?,
    /** Число значимых компонент выхода (1..4); влияет только на семантику. */
    val components: Int,
    /** RPN-условие применения прохода; null → применять всегда. */
    val condition: RpnExpression?,
    /** Тело GLSL: всё между директивами (обычно `vec4 hook() { ... }`). */
    val body: String,
)
