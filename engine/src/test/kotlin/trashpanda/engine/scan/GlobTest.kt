package trashpanda.engine.scan

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobTest {

    @Test
    fun `double-star spans segments`() {
        assertTrue(Glob.matches("C:/u/.gradle/caches/**", "C:/u/.gradle/caches/modules-2/foo.jar"))
        assertTrue(Glob.matches("C:/u/.gradle/caches/**", "C:/u/.gradle/caches/x"))
        assertFalse(Glob.matches("C:/u/.gradle/caches/**", "C:/u/.gradle/caches"))
        assertFalse(Glob.matches("C:/u/.gradle/caches/**", "C:/u/.gradle/other/x"))
    }

    @Test
    fun `leading double-star spans anything`() {
        assertTrue(Glob.matches("**/node_modules", "git/web/x/node_modules"))
        assertTrue(Glob.matches("**/node_modules", "node_modules"))
        assertFalse(Glob.matches("**/node_modules", "git/web/x/node_modules/core-js"))
    }

    @Test
    fun `single-star stays inside one segment`() {
        assertTrue(Glob.matches("C:/t/chromium-*", "C:/t/chromium-1234"))
        assertFalse(Glob.matches("C:/t/chromium-*", "C:/t/sub/chromium-1"))
    }

    @Test
    fun `windows backslashes match forward-slash patterns`() {
        assertTrue(Glob.matches("C:/Users/x/Temp/**", "C:\\Users\\x\\Temp\\a\\b.tmp"))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertTrue(Glob.matches("C:/Users/x/temp/**", "c:/users/x/TEMP/a.txt"))
    }
}
