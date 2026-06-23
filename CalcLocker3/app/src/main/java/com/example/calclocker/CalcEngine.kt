package com.example.calclocker

/**
 * A small recursive-descent evaluator so the disguise is a fully working calculator.
 * Supports + - × ÷, decimals, unary minus and parentheses (engine-side; the UI just
 * doesn't expose paren buttons). Returns null on any malformed input.
 *
 *   expr   := term (('+' | '-') term)*
 *   term   := factor (('*' | '/') factor)*
 *   factor := ('+' | '-') factor | number | '(' expr ')'
 */
object CalcEngine {

    fun evaluate(raw: String): Double? {
        val expr = raw.replace('×', '*').replace('÷', '/').replace('−', '-')
        return try {
            val p = Parser(tokenize(expr))
            val v = p.parseExpr()
            if (!p.atEnd() || v.isNaN() || v.isInfinite()) null else v
        } catch (e: Exception) {
            null
        }
    }

    private fun tokenize(s: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val sb = StringBuilder()
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) { sb.append(s[i]); i++ }
                    out.add(sb.toString())
                }
                c in "+-*/()" -> { out.add(c.toString()); i++ }
                else -> throw IllegalArgumentException("bad char: $c")
            }
        }
        return out
    }

    private class Parser(val t: List<String>) {
        private var i = 0
        fun atEnd() = i >= t.size
        private fun peek() = if (i < t.size) t[i] else null
        private fun next() = t[i++]

        fun parseExpr(): Double {
            var v = parseTerm()
            while (peek() == "+" || peek() == "-") {
                val op = next()
                val r = parseTerm()
                v = if (op == "+") v + r else v - r
            }
            return v
        }

        private fun parseTerm(): Double {
            var v = parseFactor()
            while (peek() == "*" || peek() == "/") {
                val op = next()
                val r = parseFactor()
                v = if (op == "*") v * r else v / r
            }
            return v
        }

        private fun parseFactor(): Double {
            val p = peek() ?: throw IllegalArgumentException("unexpected end")
            if (p == "+") { next(); return parseFactor() }
            if (p == "-") { next(); return -parseFactor() }
            if (p == "(") {
                next()
                val v = parseExpr()
                if (peek() != ")") throw IllegalArgumentException("expected )")
                next()
                return v
            }
            return next().toDouble()
        }
    }
}
