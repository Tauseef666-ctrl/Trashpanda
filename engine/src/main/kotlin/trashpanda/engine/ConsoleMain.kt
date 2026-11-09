package trashpanda.engine

import trashpanda.engine.execute.Executor
import trashpanda.engine.execute.RecycleOut
import trashpanda.engine.execute.RecyclePort
import trashpanda.engine.execute.ReceiptStore
import trashpanda.engine.execute.Restorer
import trashpanda.engine.execute.Status
import trashpanda.engine.interview.Interview
import trashpanda.engine.report.ConsoleReport
import trashpanda.engine.report.Sizes
import trashpanda.engine.scan.Drives
import trashpanda.engine.scan.Planner
import trashpanda.platform.winapi.ShellOp
import trashpanda.rules.RuleCatalog
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Console face of TrashPanda (PLAN P1/P2). Scans are read-only ALWAYS; the disk only
 * changes when BOTH --ask and --execute are given, and then only into the Recycle Bin.
 *
 *   (no args)            banner + rule db summary
 *   --temp               dry-run scan of %LOCALAPPDATA%\Temp (D1's real target)
 *   --scan <dir>         dry-run scan of one directory against the full catalog
 *   --all-drives         dry-run scan of every fixed drive (whole system)
 *   --ask                guided Q&A: TrashPanda explains and asks what to delete
 *   --execute            WITH --ask: move the approved items into the Recycle Bin and
 *                        write a receipt. Without --ask it refuses. Dry run otherwise.
 *   --undo <id|latest>   restore a receipt's items from the Recycle Bin (no scan).
 */
fun main(args: Array<String>) {
    val flagSet = args.toSet()
    val undoIndex = args.indexOf("--undo")

    // Undo is self-contained: receipt -> bin -> back home. No scan, no approval step.
    if (undoIndex == 0 && args.size >= 2) {
        undo(args[1])
        return
    }
    if (undoIndex >= 0) {
        System.err.println("--undo expects exactly one argument: a receipt id or 'latest'")
        return
    }

    val catalog = RuleCatalog.loadBuiltin()
    println("rule db: ${catalog.rules.size} rules loaded from ${RuleCatalog.RESOURCE}")
    println("never-touch tokens: ${catalog.neverTouch.size} (git, ssh, wallets, cookies…)")

    val ask = "--ask" in flagSet
    val execute = "--execute" in flagSet
    val roots: List<Path> = when {
        "--temp" in flagSet -> listOf(Path.of(System.getenv("LOCALAPPDATA"), "Temp"))
        "--scan" in flagSet -> {
            val target = args.getOrNull(args.indexOf("--scan") + 1)
            if (target == null) {
                System.err.println("--scan needs a directory argument")
                return
            }
            listOf(Path.of(target))
        }
        "--all-drives" in flagSet -> Drives.fixedDrives().map { it.root }
        else -> listOf(Path.of(System.getenv("USERPROFILE")))
    }

    if (execute && !ask) {
        System.err.println("refusing: : --execute needs --ask — deletion is an explicit guided step, never a side effect")
        return
    }

    val missing = roots.filter { !it.exists() }
    if (missing.isNotEmpty()) System.err.println("skipped (missing): $missing")
    val present = roots.filter { it.exists() }
    if (present.isEmpty()) return

    if ("--all-drives" in flagSet) {
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
        println(
            "Interview complete - approved: ${selection.approved.size} item(s), ${Sizes.human(selection.approvedBytes)}",
        )

        if (!execute) {
            println("dry run - nothing was moved. Add --execute to move approved items into the Recycle Bin.")
            return
        }

        println()
        println("Moving approved items to the Recycle Bin (reversible until that bin is emptied)...")
        val port = RecyclePort { path -> ShellOp.recycleToBin(path).let { RecycleOut(it.code, it.aborted) } }
        val store = ReceiptStore()
        val receipt = Executor(port, store).execute(selection.approved)
        println()
        println("Receipt: ${receipt.id}")
        println("  moved to bin: ${receipt.recycled.size} item(s) - ${Sizes.human(receipt.freedBytes)}")
        if (receipt.failed.isNotEmpty()) {
            println(
                "  left alone:   ${receipt.failed.size} item(s) - ${Sizes.human(receipt.failedBytes)} (locked or protected)",
            )
        }
        println("  saved at:     ${store.dir}\\${receipt.id}.json")
        println("  undo now:     gradlew :engine:run --args=\"--undo ${receipt.id}\"")
    } else {
        println(ConsoleReport.render(plan, elapsed))
    }
}

/** --undo <id|latest>: read receipt, restore from bin, print the item-by-item story. */
private fun undo(target: String) {
    val store = ReceiptStore()
    val receipt = when (target.lowercase()) {
        "latest" -> store.latest()
        else -> store.list().firstOrNull { it.id == target }
    }
    if (receipt == null) {
        println("no receipt found for '$target' under ${store.dir}")
        return
    }
    println("Receipt ${receipt.id} - ${receipt.recycled.size} item(s) currently in the bin")
    val restorer = Restorer(store)
    val undone = restorer.restore(receipt)
    undone.items.forEach { item ->
        when (item.status) {
            Status.RESTORED ->
                println("  restored: ${item.originalPath} -> ${item.restoredTo} (${Sizes.human(item.sizeBytes)})")
            Status.RECYCLED ->
                println("  still in bin (kept): ${item.originalPath} - ${item.error}")
            Status.FAILED ->
                println("  could not restore: ${item.originalPath} - ${item.error}")
        }
    }
    println("Receipt state: ${undone.state}")
}
