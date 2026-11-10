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
    fun `saving a theme writes it explicitly into settings json`() {
        val file = Files.createTempDirectory("tp-settings-test").resolve("settings.json")
        try {
            Settings.saveTheme(ThemeName.DARK_GLASS, file)
            val text = Files.readString(file)
            // Regression: a live install once wrote `{}`. The DTO carries no default, so
            // serializing ANY theme (even the default) must name the key explicitly.
            assertTrue(
                text.contains("\"theme\": \"DARK_GLASS\""),
                "settings.json must always carry the theme key explicitly: $text",
            )
            assertEquals(ThemeName.DARK_GLASS, Settings.loadTheme(file))
        } finally {
            file.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `missing or corrupt settings never block the app`() {
        file { path ->
            // missing file → default (sandbox path was never written)
            assertEquals(ThemeName.DARK_GLASS, Settings.loadTheme(path))
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
