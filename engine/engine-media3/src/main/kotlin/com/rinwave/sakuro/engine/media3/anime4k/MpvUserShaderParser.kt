package com.rinwave.sakuro.engine.media3.anime4k

/**
 * Парсер формата mpv user-shaders (libplacebo hook-language) в список
 * [UserShaderPass]. Разбивает файл `.glsl` по директивам `//!` на проходы;
 * тело каждого прохода — GLSL между блоком директив и следующим `//!DESC`
 * (или концом файла).
 *
 * Поддерживаемые директивы: `DESC HOOK BIND SAVE WIDTH HEIGHT COMPONENTS WHEN`.
 * Незнакомые директивы (`OFFSET`, `WHEN` уже есть, `COMPUTE` и т.п.) осознанно
 * игнорируются — Anime4K v4.0.1 их не использует. Начальный лицензионный
 * комментарий и любые строки до первого `//!HOOK` отбрасываются.
 *
 * Дизайн из docs/anime4k-media3-port-plan.md §3.1: тела проходов почти готовый
 * GLSL, парсеру нужно лишь разложить директивы и границы.
 */
internal object MpvUserShaderParser {

    private const val PREFIX = "//!"

    fun parse(source: String): List<UserShaderPass> {
        val passes = mutableListOf<UserShaderPass>()
        var current: DirectiveBlock? = null
        val body = StringBuilder()

        fun flush() {
            current?.let { passes += it.toPass(body.toString()) }
            body.setLength(0)
        }

        for (rawLine in source.lineSequence()) {
            val line = rawLine.trim()
            // Новый проход начинается на `//!DESC`, а если DESC нет — на первом `//!HOOK`.
            val startsPass = line.startsWith(PREFIX + "DESC") ||
                (line.startsWith(PREFIX + "HOOK") && current == null)
            when {
                startsPass -> {
                    if (line.startsWith(PREFIX + "DESC")) flush()
                    current = DirectiveBlock().apply { applyDirective(line) }
                }
                line.startsWith(PREFIX) -> current?.applyDirective(line) // до первого прохода игнор
                current != null -> body.appendLine(rawLine)
                // строки до первого прохода (лицензия) отбрасываем
            }
        }
        flush()
        return passes
    }

    /** Накопитель директив одного прохода до появления его тела. */
    private class DirectiveBlock {
        private var desc = ""
        private var hook = "MAIN"
        private val binds = mutableListOf<String>()
        private var save: String? = null
        private var width: RpnExpression? = null
        private var height: RpnExpression? = null
        private var components = DEFAULT_COMPONENTS
        private var condition: RpnExpression? = null

        fun applyDirective(line: String) {
            val content = line.removePrefix(PREFIX).trim()
            val keyword = content.substringBefore(' ')
            val value = content.substringAfter(' ', "").trim()
            when (keyword) {
                "DESC" -> desc = value
                "HOOK" -> hook = value
                "BIND" -> if (value.isNotEmpty()) binds += value
                "SAVE" -> save = value
                "WIDTH" -> width = RpnExpression.parse(value)
                "HEIGHT" -> height = RpnExpression.parse(value)
                "COMPONENTS" -> components = value.toIntOrNull() ?: DEFAULT_COMPONENTS
                "WHEN" -> condition = RpnExpression.parse(value)
                else -> Unit // OFFSET/COMPUTE/… — не используются Anime4K v4.0.1
            }
        }

        fun toPass(body: String): UserShaderPass = UserShaderPass(
            desc = desc,
            hook = hook,
            // Без явного BIND проход всё равно читает хукнутую стадию.
            binds = if (binds.isEmpty()) listOf(HOOKED) else binds.toList(),
            save = save ?: HOOKED,
            width = width,
            height = height,
            components = components,
            condition = condition,
            body = body.trimEnd(),
        )
    }

    /** Псевдо-имя текущей хукнутой стадии в mpv (`HOOKED_tex`, `SAVE HOOKED`). */
    const val HOOKED = "HOOKED"
    private const val DEFAULT_COMPONENTS = 4
}
