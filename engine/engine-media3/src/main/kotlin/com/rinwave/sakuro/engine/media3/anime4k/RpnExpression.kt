package com.rinwave.sakuro.engine.media3.anime4k

/**
 * Мини-эвалюатор выражений mpv user-shaders в обратной польской записи (RPN).
 *
 * Директивы `//!WIDTH`, `//!HEIGHT`, `//!WHEN` задают формулы в RPN, где
 * операнды — числовые литералы либо ссылки на размеры текстур вида `MAIN.w`,
 * `conv2d_last_tf.h`, `OUTPUT.w` (libplacebo hook-language). Пример:
 * `conv2d_last_tf.w 2 *` (ширина ×2), `OUTPUT.w MAIN.w / 1.200 > OUTPUT.h MAIN.h / 1.200 > *`.
 *
 * Поддерживаемые операторы: `+ - * /` (арифметика), `> < >= <= =` (сравнение,
 * дают 1.0/0.0), `* +` над булевыми значениями работают как AND/OR по правилу
 * «ненулевое = истина». Разбор — один раз при парсинге шейдера; вычисление —
 * при [RpnExpression.eval] с текущими размерами текстур.
 */
internal class RpnExpression private constructor(private val tokens: List<String>) {

    /**
     * Вычисляет выражение. [resolve] отдаёт значение переменной вида `MAIN.w`;
     * для неизвестной переменной должен бросить исключение (баг в шейдере/графе).
     */
    fun eval(resolve: (String) -> Float): Float {
        val stack = ArrayDeque<Float>()
        for (token in tokens) {
            val op = OPERATORS[token]
            if (op != null) {
                val b = stack.removeLastOrNull() ?: error("RPN: недостаточно операндов для '$token' в $tokens")
                val a = stack.removeLastOrNull() ?: error("RPN: недостаточно операндов для '$token' в $tokens")
                stack.addLast(op(a, b))
            } else {
                stack.addLast(token.toFloatOrNull() ?: resolve(token))
            }
        }
        return stack.singleOrNull() ?: error("RPN: выражение не свелось к одному значению: $tokens")
    }

    /** Истинно, если результат ненулевой (соглашение mpv для `//!WHEN`). */
    fun isTruthy(resolve: (String) -> Float): Boolean = eval(resolve) != 0f

    companion object {
        private val OPERATORS: Map<String, (Float, Float) -> Float> = mapOf(
            "+" to { a, b -> a + b },
            "-" to { a, b -> a - b },
            "*" to { a, b -> a * b },
            "/" to { a, b -> a / b },
            ">" to { a, b -> if (a > b) 1f else 0f },
            "<" to { a, b -> if (a < b) 1f else 0f },
            ">=" to { a, b -> if (a >= b) 1f else 0f },
            "<=" to { a, b -> if (a <= b) 1f else 0f },
            "=" to { a, b -> if (a == b) 1f else 0f },
        )

        /** Разбивает строку директивы по пробелам в токены RPN. */
        fun parse(expression: String): RpnExpression {
            val tokens = expression.trim().split(WHITESPACE).filter { it.isNotEmpty() }
            require(tokens.isNotEmpty()) { "RPN: пустое выражение" }
            return RpnExpression(tokens)
        }

        private val WHITESPACE = Regex("\\s+")
    }
}
