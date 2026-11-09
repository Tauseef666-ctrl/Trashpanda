package trashpanda.platform.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvExpandTest {

    private val env = mapOf(
        "USERPROFILE" to "C:\\Users\\tester",
        "LOCALAPPDATA" to "C:\\Users\\tester\\AppData\\Local",
        "APPDATA" to "C:\\Users\\tester\\AppData\\Roaming",
    )

    @Test
    fun `expands known variables`() {
        // Env values keep their backslashes; literal separators from the pattern stay as typed.
        // Matching normalizes separators downstream (Glob), so mixed output is by design.
        assertEquals(
            "C:\\Users\\tester\\AppData\\Local/Temp/x",
            EnvExpand.expand("%LOCALAPPDATA%/Temp/x", env),
        )
        assertEquals(
            "C:\\Users\\tester/.gradle/caches",
            EnvExpand.expand("%USERPROFILE%/.gradle/caches", env),
        )
    }

    @Test
    fun `expands tilde`() {
        assertEquals("C:\\Users\\tester/.ollama/models", EnvExpand.expand("~/.ollama/models", env))
        assertEquals("C:\\Users\\tester", EnvExpand.expand("~", env))
    }

    @Test
    fun `unknown variables stay visible - never silently resolved`() {
        assertEquals("%NOPE%/x", EnvExpand.expand("%NOPE%/x", env))
    }
}

class PathTextTest {

    @Test
    fun `prefixes long path strings`() {
        val deep = buildString { append("C:\\"); repeat(40) { append("folder\\") } }
        val prefixed = PathText.prefix(deep)
        assertTrue(prefixed.startsWith("\\\\?\\"), prefixed)
        assertTrue(prefixed.endsWith("\\"), prefixed)
    }

    @Test
    fun `prefixes UNC paths with the UNC device form`() {
        assertEquals("\\\\?\\UNC\\server\\share", PathText.prefix("\\\\server\\share"))
    }

    @Test
    fun `is idempotent`() {
        val once = PathText.prefix("C:\\Windows\\Temp")
        assertEquals(once, PathText.prefix(once))
    }

    @Test
    fun `does not touch short paths`() {
        assertEquals("C:\\Windows\\Temp", PathText.maybePrefix("C:\\Windows\\Temp"))
    }
}
