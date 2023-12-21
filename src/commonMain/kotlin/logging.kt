private val DEBUG = getenv("DEBUG") !in listOf(null, "", "0", "false", "FALSE")

fun debug(vararg args: Any) { if (DEBUG) report("DEBUG", *args) }

fun debugList(message: String, items: Collection<String>) {
    debug()
    debug(message)
    if (items.isEmpty()) debug("<empty>")
    items.forEach { debug("* $it") }
}

fun warn(vararg args: Any) { report("WARNING", *args) }

private fun report(prefix: String, vararg args: Any) {
    printlnErr(buildString {
        append("[$prefix] ")
        args.forEach { append(it) }
    })
}
