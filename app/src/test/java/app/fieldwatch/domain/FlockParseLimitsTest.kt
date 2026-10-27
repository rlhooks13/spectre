package app.fieldwatch.domain

import java.io.StringReader
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** P0-7c: hostile or broken lists fail the parse instead of exhausting the stack or memory. */
class FlockParseLimitsTest {
    private fun rejects(json: String, why: String) {
        try {
            parseFlock(StringReader(json))
            fail("accepted: $why")
        } catch (e: FlockParseException) {
            assertTrue(e.message.orEmpty(), true)
        }
    }

    @Test
    fun deepNestingIsAParseErrorNotAStackOverflow() {
        val depth = 100_000
        rejects("""{"x":${"[".repeat(depth)}${"]".repeat(depth)},"features":[]}""", "deep array")
        rejects("""{"features":[{"properties":${"{\"a\":".repeat(depth)}1${"}".repeat(depth)}}]}""", "deep object")
    }

    @Test
    fun oneHugeStringIsRejected() {
        rejects("""{"note":"${"a".repeat(1_000_000)}","features":[]}""", "1 MB string")
    }

    @Test
    fun aListThatRepeatsOneIdForeverIsRejected() {
        val one = """{"type":"Feature","geometry":{"type":"Point","coordinates":[-122.0,37.0]},"properties":{"osm_id":1,"osm_type":"node"}},"""
        try {
            parseFlock(Endless("""{"features":[""", one))
            fail("accepted an endless list of one id")
        } catch (_: FlockParseException) {
        }
    }

    /** A reader that never ends: a head, then [unit] forever, without holding it all in memory. */
    private class Endless(private val head: String, private val unit: String) : java.io.Reader() {
        private var pos = 0L

        override fun read(buf: CharArray, off: Int, len: Int): Int {
            for (i in 0 until len) {
                buf[off + i] = if (pos < head.length) head[pos.toInt()] else unit[((pos - head.length) % unit.length).toInt()]
                pos++
            }
            return len
        }

        override fun close() {}
    }
}
