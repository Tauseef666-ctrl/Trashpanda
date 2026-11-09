package trashpanda.engine

/** Console banner per DESIGN §3.3 (ASCII-safe for every Windows codepage). */
object Banner {
    fun print() {
        println("""   (\_/)""")
        println("""  (=+'.)  trashpanda v0.1 - your disk did NOT feel clean today""")
        println("""  (")_(")""")
        println()
    }
}
