package com.rinwave.sakuro.engine.media3.anime4k

/**
 * A mini evaluator for mpv user-shader expressions in reverse Polish notation (RPN).
 *
 * The `//!WIDTH`, `//!HEIGHT`, `//!WHEN` directives define formulas in RPN, where the
 * operands are numeric literals or references to texture sizes such as `MAIN.w`,
 * `conv2d_last_tf.h`, `OUTPUT.w` (libplacebo hook-language). Example:
 * `conv2d_last_tf.w 2 *` (width ×2), `OUTPUT.w MAIN.w / 1.200 > OUTPUT.h MAIN.h / 1.200 > *`.
 *
 * Supported operators: `+ - * /` (arithmetic), `> < >= <= =` (comparison,
 * yielding 1.0/0.0); `* +` over boolean values act as AND/OR under the
 * "non-zero = true" rule. Parsing happens once when the shader is parsed; evaluation
 * happens in [RpnExpression.eval] with the current texture sizes.
 */
internal class RpnExpression private constructor(private val tokens: List<String>) {

    /**
     * Evaluates the expression. [resolve] returns the value of a variable such as `MAIN.w`;
     * for an unknown variable it must throw (a bug in the shader/graph).
     */
    fun eval(resolve: (String) -> Float): Float {
        val stack = ArrayDeque<Float>()
        for (token in tokens) {
            val op = OPERATORS[token]
            if (op != null) {
                val b = stack.removeLastOrNull() ?: error("RPN: not enough operands for '$token' in $tokens")
                val a = stack.removeLastOrNull() ?: error("RPN: not enough operands for '$token' in $tokens")
                stack.addLast(op(a, b))
            } else {
                stack.addLast(token.toFloatOrNull() ?: resolve(token))
            }
        }
        return stack.singleOrNull() ?: error("RPN: expression did not reduce to a single value: $tokens")
    }

    /** True if the result is non-zero (the mpv convention for `//!WHEN`). */
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

        /** Splits a directive string on whitespace into RPN tokens. */
        fun parse(expression: String): RpnExpression {
            val tokens = expression.trim().split(WHITESPACE).filter { it.isNotEmpty() }
            require(tokens.isNotEmpty()) { "RPN: empty expression" }
            return RpnExpression(tokens)
        }

        private val WHITESPACE = Regex("\\s+")
    }
}
