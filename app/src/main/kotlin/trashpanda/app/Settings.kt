package trashpanda.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import trashpanda.platform.AppDirs
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Theme persistence — settings.json (DESIGN): %APPDATA%\Trashpanda\settings.json.
 * Corruption or absence must never block the app: every failure falls back to the
 * Dark glass default and is otherwise silent.
 */
@Serializable
private data class SettingsDto(
    val theme: String, // NO default: the key must always be written.
    val exclusions: List<String> = emptyList(), // "never touch" paths; may be absent in old files
)

object Settings {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true // keep `theme` visible even when it's the default
    }

    fun loadTheme(path: Path = AppDirs.settings): ThemeName =
        read(path)?.let { dto ->
            ThemeName.entries.firstOrNull { it.name.equals(dto.theme, ignoreCase = true) }
        } ?: ThemeName.DARK_GLASS

    fun loadExclusions(path: Path = AppDirs.settings): List<String> =
        read(path)?.exclusions?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    fun saveTheme(theme: ThemeName, path: Path = AppDirs.settings) =
        saveAll(theme, loadExclusions(path), path)

    /** Single writer: theme AND exclusions together, so neither clobbers the other. */
    fun saveAll(theme: ThemeName, exclusions: List<String>, path: Path = AppDirs.settings) {
        val cleaned = exclusions.map { it.trim() }.filter { it.isNotEmpty() }
        runCatching {
            Files.createDirectories(path.parent)
            Files.writeString(path, json.encodeToString(SettingsDto.serializer(), SettingsDto(theme.name, cleaned)))
        }
    }

    private fun read(path: Path): SettingsDto? {
        val text = runCatching { path.readText() }.getOrNull() ?: return null
        return runCatching { json.decodeFromString(SettingsDto.serializer(), text) }.getOrNull()
    }
}
