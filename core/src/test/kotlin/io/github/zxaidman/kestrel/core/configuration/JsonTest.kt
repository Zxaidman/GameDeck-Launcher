package io.github.zxaidman.kestrel.core.configuration

import io.github.zxaidman.kestrel.core.common.Outcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonTest {

    private fun parse(text: String) = Json.parse(text)

    private fun ok(text: String): ConfigNode {
        val outcome = parse(text)
        assertTrue(outcome is Outcome.Success, "expected success for `$text`, got $outcome")
        return (outcome as Outcome.Success).value
    }

    private fun rejected(text: String): ConfigurationError.MalformedDocument {
        val outcome = parse(text)
        assertTrue(outcome is Outcome.Failure, "`$text` was accepted and should not have been")
        val error = (outcome as Outcome.Failure).error
        assertTrue(error is ConfigurationError.MalformedDocument, "got $error")
        return error as ConfigurationError.MalformedDocument
    }

    // --- what it accepts ------------------------------------------------------------------------

    @Test
    fun `reads the shapes a configuration document is made of`() {
        val node = ok("""{"a": 1, "b": [true, false, null], "c": {"d": "text"}}""")
        assertEquals(
            ConfigNode.Obj(
                mapOf(
                    "a" to ConfigNode.Num(1.0),
                    "b" to ConfigNode.Arr(
                        listOf(ConfigNode.Bool(true), ConfigNode.Bool(false), ConfigNode.Null)
                    ),
                    "c" to ConfigNode.Obj(mapOf("d" to ConfigNode.Text("text"))),
                )
            ),
            node,
        )
    }

    @Test
    fun `empty containers are containers, not nothing`() {
        assertEquals(ConfigNode.Obj(emptyMap()), ok("{}"))
        assertEquals(ConfigNode.Arr(emptyList()), ok("[]"))
        assertEquals(ConfigNode.Obj(mapOf("x" to ConfigNode.Arr(emptyList()))), ok("""{"x":[]}"""))
    }

    @Test
    fun `whitespace between every token is allowed`() {
        assertEquals(
            ConfigNode.Obj(mapOf("a" to ConfigNode.Num(1.0))),
            ok("  {\n\t\"a\"  :\r\n 1 \n}  "),
        )
    }

    @Test
    fun `numbers cover the forms a layout uses`() {
        assertEquals(ConfigNode.Num(0.0), ok("0"))
        assertEquals(ConfigNode.Num(-0.5), ok("-0.5"))
        assertEquals(ConfigNode.Num(1.0), ok("1"))
        assertEquals(ConfigNode.Num(12.25), ok("12.25"))
        assertEquals(ConfigNode.Num(1500.0), ok("1.5e3"))
        assertEquals(ConfigNode.Num(0.015), ok("1.5E-2"))
    }

    @Test
    fun `escapes are decoded, including unicode`() {
        assertEquals(
            ConfigNode.Text("a\"b\\c/d\bef\ng\rh\ti"),
            ok(""" "a\"b\\c\/d\be\ff\ng\rh\ti" """),
        )
        assertEquals(ConfigNode.Text("é"), ok(""" "é" """))
    }

    // --- what it refuses ------------------------------------------------------------------------

    @Test
    fun `things that look like JSON but are not are refused`() {
        // Being permissive would mean accepting a file, writing it back differently, and leaving
        // the user to work out which of the two is what they meant.
        listOf(
            """{"a": 1,}""",           // trailing comma
            """[1, 2,]""",             // trailing comma
            """{a: 1}""",              // unquoted key
            """{'a': 1}""",            // single quotes
            """{"a": 1} // note""",    // comment
            """{"a": 01}""",           // leading zero
            """{"a": .5}""",           // no integer part
            """{"a": +1}""",           // leading plus
            """{"a": Infinity}""",     // not a JSON number
            """{"a": NaN}""",
            """{"a": 1}{"b": 2}""",    // two documents
            """{"a" 1}""",             // missing colon
            """{"a": }""",             // missing value
            "",                        // nothing at all
            "   ",
        ).forEach { rejected(it) }
    }

    @Test
    fun `an unterminated string is refused rather than silently closed`() {
        rejected("""{"a": "unfinished""")
    }

    @Test
    fun `a raw control character in text is refused`() {
        // A name carrying one cannot be displayed or logged safely.
        rejected("{\"a\": \"line\nbreak\"}")
    }

    @Test
    fun `an unknown escape is refused`() {
        rejected(""" "\q" """)
        rejected(""" "\u00zz" """)
        rejected(""" "\u12" """)
    }

    @Test
    fun `a repeated key is refused rather than resolved`() {
        // JSON does not say which one wins, so any choice here would be this reader's opinion
        // silently overriding the author's.
        val error = rejected("""{"a": 1, "a": 2}""")
        assertTrue(error.reason.contains("twice"), error.reason)
    }

    @Test
    fun `nesting deeper than the guard is refused instead of overflowing the stack`() {
        // Untrusted input, recursive descent: a crash is not a typed error.
        val deep = "[".repeat(Json.MAX_DEPTH + 5) + "]".repeat(Json.MAX_DEPTH + 5)
        val error = rejected(deep)
        assertTrue(error.reason.contains("nested"), error.reason)
    }

    @Test
    fun `nesting up to the guard is accepted, so the limit is a guard and not a ceiling`() {
        val atLimit = "[".repeat(Json.MAX_DEPTH) + "]".repeat(Json.MAX_DEPTH)
        ok(atLimit)
    }

    @Test
    fun `the offset points at where reading stopped`() {
        val error = rejected("""{"a": 1, "b": }""")
        assertEquals('}', """{"a": 1, "b": }"""[error.offset])
    }

    // --- the shape the rest of the module expects -----------------------------------------------

    @Test
    fun `a parsed document feeds the readers that were built for it`() {
        val node = ok("""{"schemaVersion": 1, "name": "Example"}""")
        val obj = (ConfigReader.asObject(node) as Outcome.Success).value
        assertEquals(1, (ConfigReader.integer(obj, "schemaVersion") as Outcome.Success).value)
        assertEquals("Example", (ConfigReader.text(obj, "name") as Outcome.Success).value)
    }
}
