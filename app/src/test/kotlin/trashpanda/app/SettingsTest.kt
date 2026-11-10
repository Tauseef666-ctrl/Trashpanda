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
            assertEquals(emptyList(), Settings.loadExclusions(path), "missing file means zero exclusions")
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

    @Test
    fun `exclusions survive a save load round trip and dedupe only by exact key`() {
        val file = Files.createTempDirectory("tp-settings-test").resolve("settings.json")
        try {
            val ex = listOf("D:\\My Games", "C:\\Users\\me\\keep.txt")
            Settings.saveAll(ThemeName.LIGHT_MILK, ex, file)
            assertEquals(ex, Settings.loadExclusions(file), "exclusions must load back in order")
            assertEquals(ThemeName.LIGHT_MILK, Settings.loadTheme(file), "theme rides along in saveAll")
            val text = Files.readString(file)
            assertTrue(text.contains("exclusions"), "settings.json must carry the exclusions key: $text")

            // A save with an empty list clears them; blank entries never persist.
            Settings.saveAll(ThemeName.LIGHT_MILK, listOf(" ", ""), file)
            assertEquals(emptyList(), Settings.loadExclusions(file))
        } finally {
            file.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `saving a theme keeps already-stored exclusions`() {
        val file = Files.createTempDirectory("tp-settings-test").resolve("settings.json")
        try {
            Settings.saveAll(ThemeName.DARK_GLASS, listOf("D:\\precious"), file)
            Settings.saveTheme(ThemeName.TERMINAL_NEON, file)
            assertEquals(listOf("D:\\precious"), Settings.loadExclusions(file), "theme change must not clobber exclusions")
            assertEquals(ThemeName.TERMINAL_NEON, Settings.loadTheme(file))
        } finally {
            file.parent.toFile().deleteRecursively()
        }
    }

    @Test
    fun `an old theme-only settings json still loads`() {
        val dir = Files.createTempDirectory("tp-settings-test")
        try {
            val file = dir.resolve("settings.json")
            Files.writeString(file, """{"theme": "TERMINAL_NEON"}""")
            assertEquals(ThemeName.TERMINAL_NEON, Settings.loadTheme(file))
            assertEquals(emptyList(), Settings.loadExclusions(file), "old file without exclusions loads as none")
        } finally {
            dir.toFile().deleteRecursively()
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
