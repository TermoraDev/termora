package app.termora

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntPathMatcherTest {
    @Test
    fun test() {
        val matcher = AntPathMatcher(".")
        assertTrue(matcher.match("*.baidu.com", "www.baidu.com"))
        assertTrue(matcher.match("*.baidu.com", "wwwwwwwwwwww123123aaa.baidu.com"))
        assertFalse(matcher.match("*.baidu.com", "sub.sub.baidu.com"))
        assertTrue(matcher.match("**.baidu.com", "sub.sub.baidu.com"))
    }
}