package app.termora

import org.semver4j.Semver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SemverTest {
    @Test
    fun test() {
        val a = Semver.parse("1.0.0") ?: throw IllegalArgumentException("Semver is invalid")
        assertTrue(a.satisfies("1.0.0 - 2.0.0"))
        assertTrue(a.satisfies(">=1.0.0"))
    }

    @Test
    fun testBeta() {
        val a = Semver.parse("2.0.0-beta.1") ?: return
        val b = Semver.parse("2.0.0-beta.2") ?: return
        val c = Semver.parse("2.0.1-beta.2") ?: return

        assertTrue(a.compareTo(b) == -1)
        assertTrue(a.compareTo(c) == -1)

        val list = listOf(b, c, a)
        println(list.sortedBy { it })
        println(list.sortedByDescending { it })

        assertEquals(list.minBy { it }, a)
        assertEquals(list.maxBy { it }, c)
    }

}