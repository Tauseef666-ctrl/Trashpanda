package trashpanda.engine

import trashpanda.engine.interview.Interview
import trashpanda.engine.report.ConsoleReport
import trashpanda.engine.report.Sizes
import trashpanda.engine.scan.Drives
import trashpanda.engine.scan.Planner
import trashpanda.rules.RuleCatalog
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Console face of TrashPanda (PLAN P1). Scan reads ONLY — nothing is ever deleted here.
 *
 *   (no args)          banner + rule catalog summary
 *   --temp             dry-run scan of %LOCALAPPDATA%\Temp (D1's real target)
 *   --scan <dir>       dry-run scan of one directory against the full catalog
 *   --all-drives       dry-run scan of every fixed drive (whole system)
 *   --ask              guided Q&A: TrashPanda explains and asks what to delete
 *                      (combine with --temp / --scan / --all-drives; default = user profile)
 */
fun main(args: Array<String>) {
    Banner.print()

    val catalog = RuleCatalog.loadBuiltin()
    println("rule db: ${catalog.rules.size} rules loaded from ${RuleCatalog.RESOURCE}")
    println("never-touch tokens: ${catalog.neverTouch.size} (git, ssh, wallets, cookies…)")

    val ask = args.contains("--ask")
    val roots: List<Path> = when {
        args.contains("--temp") -> listOf(Path.of(System.getenv("LOCALAPPDATA"), "Temp"))
        args.contains("--scan") -> {
            val idx = args.indexOf("--scan")
            val target = args.getOrNull(idx + 1)
            if (target == null) {
                System.err.println("--scan needs a directory argument")
                return
            }
            listOf(Path.of(target))
        }
        args.contains("--all-drives") -> Drives.fixedDrives().map { it.root }
        else -> listOf(Path.of(System.getenv("USERPROFILE")))
    }

    val missing = roots.filter { !it.exists() }
    if (missing.isNotEmpty()) System.err.println("skipped (missing): $missing")
    val present = roots.filter { it.exists() }
    if (present.isEmpty()) return

    if (args.contains("--all-drives")) {
        println()
        println("Drives found:")
        Drives.fixedDrives().forEach {
            println(
                "  ${it.label} - ${Sizes.human(it.totalBytes)} total - ${Sizes.human(it.freeBytes)} free",
            )
        }
    }

    println()
    println("Scanning (read-only): $present")
    val planner = Planner(catalog)
    val started = System.currentTimeMillis()
    val plan = planner.planFromDisk(present)
    val elapsed = System.currentTimeMillis() - started

    if (ask) {
        val selection = Interview(plan, out = ::println, read = ::readlnOrNull).run()
        println()
        println("Interview complete — approved: ${selection.approved.size} item(s), ${Sizes.human(selection.approvedBytes)}")
    } else {
        println(ConsoleReport.render(plan, elapsed))
    }
}
