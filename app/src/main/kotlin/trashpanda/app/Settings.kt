package trashpanda.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import trashpanda.platform.AppDirs
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Theme persistence — settings.json (DESIGN): %APPDATA%\TrashPanda\settings.json.
 * Corruption or absence must never block the app: every failure falls back to the
 * Dark glass default and is otherwise silent.
 */
@Serializable
private data class SettingsDto(val theme: String = ThemeName.DARK_GLASS.name)

object Settings {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun loadTheme(path: Path = AppDirs.settings): ThemeName {
        val text = runCatching { path.readText() }.getOrNull() ?: return ThemeName.DARK_GLASS
        val dto = runCatching { json.decodeFromString(SettingsDto.serializer(), text) }.getOrNull()
            ?: return ThemeName.DARK_GLASS
        return ThemeName.entries.firstOrNull { it.name.equals(dto.theme, ignoreCase = true) }
            ?: ThemeName.DARK_GLASS
    }

    fun saveTheme(theme: ThemeName, path: Path = AppDirs.settings) {
        runCatching {
            Files.createDirectories(path.parent)
            Files.writeString(path, json.encodeToString(SettingsDto.serializer(), SettingsDto(theme.name)))
        }
    }
}
