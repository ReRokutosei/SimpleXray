package com.simplexray.re.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class BracketMatcherTransformationTest {

    @Test
    fun testSimpleMatching() {
        val text = "{ \"hello\": \"world\" }"
        // Cursor right on '{' (index 0)
        val matchFromOpen = findMatchingBrackets(text, 0)
        assertNotNull(matchFromOpen)
        assertEquals(0 to 19, matchFromOpen)

        // Cursor right after '{' (cursor 1)
        val matchAfterOpen = findMatchingBrackets(text, 1)
        assertEquals(0 to 19, matchAfterOpen)

        // Cursor right on '}' (index 19)
        val matchOnClose = findMatchingBrackets(text, 19)
        assertEquals(0 to 19, matchOnClose)

        // Cursor right after '}' (cursor 20)
        val matchAfterClose = findMatchingBrackets(text, 20)
        assertEquals(0 to 19, matchAfterClose)
    }

    @Test
    fun testNestedMatching() {
        val text = """{ "inbounds": [ { "port": 1080 } ] }"""
        // indices:
        // 0: {
        // 14: [
        // 16: {
        // 31: }
        // 33: ]
        // 35: }

        // Match for outermost {
        assertEquals(0 to 35, findMatchingBrackets(text, 0))
        assertEquals(0 to 35, findMatchingBrackets(text, 36))

        // Match for [
        assertEquals(14 to 33, findMatchingBrackets(text, 14))
        assertEquals(14 to 33, findMatchingBrackets(text, 34))

        // Match for inner {
        assertEquals(16 to 31, findMatchingBrackets(text, 16))
        assertEquals(16 to 31, findMatchingBrackets(text, 32))
    }

    @Test
    fun testBracketsInsideStringsAreIgnored() {
        val text = """{ "fake": "}", "list": [ "]" ] }"""
        // The '}' at index 11 is inside a string, so it should not match index 0.
        // The outer '}' is at the end of the text.
        val outerMatch = findMatchingBrackets(text, 0)
        assertNotNull(outerMatch)
        assertEquals(0, outerMatch!!.first)
        assertEquals(text.length - 1, outerMatch.second)

        // Cursor placed on the fake '}' inside the string: should return null
        val fakeBraceIndex = text.indexOf("}")
        val matchFake = findMatchingBrackets(text, fakeBraceIndex)
        assertNull(matchFake)
    }

    @Test
    fun testEscapedQuotesInString() {
        val text = """{ "key": "escaped \" } quote" }"""
        val match = findMatchingBrackets(text, 0)
        assertNotNull(match)
        assertEquals(0 to text.length - 1, match)
    }

    @Test
    fun testCursorNotInBracketReturnsNull() {
        val text = """{ "abc": 123 }"""
        // cursor in middle of "abc"
        assertNull(findMatchingBrackets(text, 5))
    }

    @Test
    fun testUnmatchedBracketsReturnNull() {
        val text = "{ [ }"
        // At index 2 ('['), no matching ']' exists
        assertNull(findMatchingBrackets(text, 2))
    }

    @Test
    fun testLargeDocumentPerformanceLinearTime() {
        // Construct a 50KB JSON-like document with hundreds of nested objects and arrays
        val sb = StringBuilder()
        sb.append("{\n")
        for (i in 0 until 500) {
            sb.append("""  "section_$i": [ { "id": $i, "dummy": "value_$i" } ],${"\n"}""")
        }
        sb.append("  \"final\": true\n")
        sb.append("}")
        val largeText = sb.toString()
        assertTrue(largeText.length > 25000)

        val lastCloseBraceIndex = largeText.length - 1

        // Benchmark finding the opening bracket from the very last closing bracket
        // With O(N^2), this would execute 25,000 * 25,000 / 2 = ~300M ops (seconds on JVM)
        // With O(N), it executes in single-digit milliseconds!
        var match: Pair<Int, Int>? = null
        val durationMs = measureTimeMillis {
            repeat(10) {
                match = findMatchingBrackets(largeText, lastCloseBraceIndex)
            }
        }

        assertNotNull(match)
        val nonNullMatch = match!!
        assertEquals(0, nonNullMatch.first)
        assertEquals(lastCloseBraceIndex, nonNullMatch.second)
        // 10 runs of matching 25KB+ must take well under 200ms
        assertTrue("Expected linear time execution, took $durationMs ms", durationMs < 200)
    }
}
