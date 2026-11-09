package trashpanda.app

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsTest {

    @Test
    fun `theme survives a save load round trip`() {
        val file = Files.createTempDirectory("tp-settings-test").resolve("settings.json")
        try {
            for (theme in ThemeName.entries) {
                Settings.saveTheme(theme, file)
                assertEquals(theme, Settings.loadTheme(file), "saved theme must load back")
            }
            val text = Files.readString(file)
            assertTrue(text.contains("theme"), "settings.json must be human-readable: $text")
        } finally {
            file.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `missing or corrupt settings never block the app`() {
        file {
            // missing file → default
            assertEquals(ThemeName.DARK_GLASS, Settings.loadTheme())
        }
        file { path ->
            // corrupt file → default
            Files.writeString(path, "{ not json ")
            assertEquals(ThemeName.DARK_GLASS, Settings.loadTheme(path))
        }
        file { path ->
            // unknown theme string → default (strict parse, never guess)
            Files.writeString(path, """{"theme": "hot-pink"}""")
            assertEquals(ThemeName.DARK_GLASS, Settings.loadTheme(path))
        }
    }

    private inline fun file(block: (java.nio.file.Path) -> Unit) {
        val dir = Files.createTempDirectory("tp-settings-test")
        try {
            block(dir.resolve("settings.json"))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
