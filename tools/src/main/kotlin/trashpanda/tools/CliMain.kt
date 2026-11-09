package trashpanda.tools

/**
 * v0.x stub (PLAN.txt P0). The real CLI (`trashpanda --dry-run --json`) is v0.3 scope.
 */
fun main(args: Array<String>) {
    println("trashpanda tools — stub")
    println("the CLI lands in v0.3: trashpanda --dry-run --json")
    if (args.isNotEmpty()) println("args ignored for now: " + args.joinToString(" "))
}
