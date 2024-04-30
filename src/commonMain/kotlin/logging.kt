// Functions for emitting log messages.

import platform.posix.exit

private const val EXIT_CODE_ON_FAIL = 20

var debugMode = getenv("DEBUG") !in listOf(null, "", "0", "false", "FALSE")

fun debug(vararg args: Any) { if (debugMode) report("DEBUG", *args) }

fun debugList(message: String, items: Collection<String>) {
    debug()
    debug(message)
    if (items.isEmpty()) debug("<empty>")
    items.forEach { debug("* $it") }
}

fun warn(vararg args: Any) { report("WARNING", *args) }

fun fail(message: String): Nothing {
    println("ERROR")
    val lines = message.split(NL)
    println(lines.size + 1)
    println(EXIT_CODE_ON_FAIL)
    lines.forEach { println(it) }
    exit(EXIT_CODE_ON_FAIL)
    // Unreachable code, but satisfies the Kotlin compiler.
    throw IllegalStateException(message)
}

private fun report(prefix: String, vararg args: Any) {
    printlnErr(buildString {
        append("[$prefix] ")
        args.forEach { append(it) }
    })
}
