package io.github.zxaidman.kestrel.core.configuration

import io.github.zxaidman.kestrel.core.common.Outcome

/**
 * Turns JSON text into a [ConfigNode], and refuses anything that is not JSON.
 *
 * **Why this is here rather than a dependency.** `core` is plain Kotlin on purpose — it is where the
 * rules live, and it must be testable without a device or an emulator. Every configuration rule in
 * this module operates on `ConfigNode`, so without something that produces one from text, the only
 * place a document could be read is `platform/`, and the only place it could be *tested* is on a
 * phone. That would put the validation this project depends on behind the slowest feedback loop it
 * has. A reader small enough to hold in one file buys the fast loop back, and costs no dependency.
 *
 * **Strict on purpose.** An imported document is untrusted input (`SECURITY.md`), so this accepts
 * JSON and not the various things that look like it: no comments, no trailing commas, no unquoted
 * keys, no single quotes, no trailing content after the value. Being permissive here would mean
 * accepting a file, writing it back out differently, and leaving the user to work out which of the
 * two is what they meant.
 *
 * Numbers are all [Double], matching `ConfigNode.Num`. JSON has one number type and so does this;
 * whether a value is an integer is a question for the field that reads it, and `ConfigReader`
 * already asks it.
 */
public object Json {

    /**
     * How deeply one document may nest.
     *
     * A guard rather than a limit anyone will meet: a layout is two levels deep. Recursive descent
     * on untrusted input without a cap is a stack overflow waiting for a file with ten thousand
     * open brackets, and a crash is not a typed error.
     */
    public const val MAX_DEPTH: Int = 32

    public fun parse(text: String): Outcome<ConfigNode> {
        val reader = Reader(text)
        return try {
            reader.skipWhitespace()
            val value = reader.readValue(0)
            reader.skipWhitespace()
            if (!reader.atEnd) {
                fail(reader.position, "unexpected text after the value")
            } else {
                Outcome.Success(value)
            }
        } catch (e: Malformed) {
            fail(e.offset, e.reason)
        }
    }

    private fun fail(offset: Int, reason: String): Outcome<ConfigNode> =
        Outcome.Failure(ConfigurationError.MalformedDocument(offset, reason))

    /** Thrown and caught within this file only. Callers see an [Outcome]. */
    private class Malformed(val offset: Int, val reason: String) : Exception(reason)

    private class Reader(private val text: String) {

        var position: Int = 0
            private set

        val atEnd: Boolean get() = position >= text.length

        private fun peek(): Char =
            if (atEnd) throw Malformed(position, "the document ended early") else text[position]

        private fun next(): Char = peek().also { position += 1 }

        private fun expect(c: Char) {
            if (atEnd || text[position] != c) {
                throw Malformed(position, "expected '$c'")
            }
            position += 1
        }

        fun skipWhitespace() {
            while (!atEnd && text[position].isJsonWhitespace()) position += 1
        }

        fun readValue(depth: Int): ConfigNode {
            if (depth > MAX_DEPTH) throw Malformed(position, "nested more than $MAX_DEPTH deep")
            skipWhitespace()
            return when (peek()) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> ConfigNode.Text(readString())
                't' -> ConfigNode.Bool(true).also { readLiteral("true") }
                'f' -> ConfigNode.Bool(false).also { readLiteral("false") }
                'n' -> ConfigNode.Null.also { readLiteral("null") }
                else -> ConfigNode.Num(readNumber())
            }
        }

        private fun readLiteral(word: String) {
            if (!text.startsWith(word, position)) throw Malformed(position, "expected '$word'")
            position += word.length
        }

        private fun readObject(depth: Int): ConfigNode.Obj {
            expect('{')
            val fields = LinkedHashMap<String, ConfigNode>()
            skipWhitespace()
            if (!atEnd && peek() == '}') {
                position += 1
                return ConfigNode.Obj(fields)
            }
            while (true) {
                skipWhitespace()
                val at = position
                val key = readString()
                // A repeated key is refused rather than resolved. JSON does not say which wins, so
                // any choice here would be this reader's opinion silently overriding the author's.
                if (fields.containsKey(key)) throw Malformed(at, "the key '$key' appears twice")
                skipWhitespace()
                expect(':')
                fields[key] = readValue(depth + 1)
                skipWhitespace()
                when (next()) {
                    ',' -> continue
                    '}' -> return ConfigNode.Obj(fields)
                    else -> throw Malformed(position - 1, "expected ',' or '}'")
                }
            }
        }

        private fun readArray(depth: Int): ConfigNode.Arr {
            expect('[')
            val items = mutableListOf<ConfigNode>()
            skipWhitespace()
            if (!atEnd && peek() == ']') {
                position += 1
                return ConfigNode.Arr(items)
            }
            while (true) {
                items += readValue(depth + 1)
                skipWhitespace()
                when (next()) {
                    ',' -> continue
                    ']' -> return ConfigNode.Arr(items)
                    else -> throw Malformed(position - 1, "expected ',' or ']'")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                when (val c = next()) {
                    '"' -> return out.toString()
                    '\\' -> out.append(readEscape())
                    else -> {
                        // Raw control characters are not legal in a JSON string, and letting one
                        // through would mean a name that cannot be displayed or logged safely.
                        if (c < ' ') throw Malformed(position - 1, "a raw control character in text")
                        out.append(c)
                    }
                }
            }
        }

        private fun readEscape(): Char = when (val c = next()) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (position + 4 > text.length) throw Malformed(position, "a short \\u escape")
                val hex = text.substring(position, position + 4)
                val value = hex.toIntOrNull(16)
                    ?: throw Malformed(position, "'$hex' is not four hexadecimal digits")
                position += 4
                value.toChar()
            }
            else -> throw Malformed(position - 1, "'\\$c' is not an escape")
        }

        private fun readNumber(): Double {
            val start = position
            if (!atEnd && peek() == '-') position += 1
            while (!atEnd && text[position].isJsonNumberPart()) position += 1
            val raw = text.substring(start, position)
            // toDoubleOrNull accepts forms JSON does not, such as "1d" and "Infinity", so the shape
            // is checked before the conversion rather than trusted to it.
            if (!NUMBER.matches(raw)) throw Malformed(start, "'$raw' is not a number")
            return raw.toDoubleOrNull() ?: throw Malformed(start, "'$raw' is not a number")
        }
    }

    private val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][-+]?[0-9]+)?")

    private fun Char.isJsonWhitespace(): Boolean =
        this == ' ' || this == '\t' || this == '\n' || this == '\r'

    private fun Char.isJsonNumberPart(): Boolean =
        this in '0'..'9' || this == '.' || this == 'e' || this == 'E' || this == '+' || this == '-'
}
