package trashpanda.platform.io

/**
 * Expands Windows environment variables (`%USERPROFILE%`) and `~` in rule patterns.
 * Unknown variables are left in place — a pattern that can't resolve must never
 * silently match some other location (TrashPanda rule 6: show the exact path).
 */
object EnvExpand {

    // %VAR% — parens/spaces allowed for names like ProgramFiles(x86)
    private val ENV = Regex("%([A-Za-z0-9_()\\- ]+)%")

    fun expand(pattern: String, env: Map<String, String> = System.getenv()): String {
        val withEnv = pattern.replace(ENV) { m ->
            env[m.groupValues[1]] ?: m.value
        }
        return expandTilde(withEnv, env)
    }

    private fun expandTilde(path: String, env: Map<String, String>): String {
        if (path == "~") return env["USERPROFILE"] ?: System.getProperty("user.home")
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            val home = env["USERPROFILE"] ?: System.getProperty("user.home")
            return home + path.drop(1)
        }
        return path
    }
}
