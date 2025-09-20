// Functions for emitting log messages.

import platform.posix.exit

private const val EXIT_CODE_ON_FAIL = 20

var debugMode = getenv("DEBUG") !in listOf(null, "", "0", "false", "FALSE")
var logFilePath = getenv("JAUNCH_LOGFILE")
private val logLines = mutableListOf<String>()
private var logFile: File? = null

/**
 * If true, directives should *not actually do anything* and instead print
 * what they *would have done* under normal (non-dry-run) circumstances.
 */
var dryRunMode = false

fun debug(vararg args: Any) { if (debugMode) report("DEBUG", *args) }

fun debugList(message: String, items: Collection<String>) {
    debug()
    debug(message)
    if (items.isEmpty()) debug("<empty>")
    items.forEach { debug("* $it") }
}

fun debugBanner(message: String) {
    val dashes = "-".repeat(message.length + 2)
    debug()
    debug("/$dashes\\")
    debug("| $message |")
    debug("\\$dashes/")
}

fun dryRun(vararg args: Any) { if (dryRunMode) report("DRY-RUN", *args) }

fun warn(vararg args: Any) { report("WARNING", *args) }

fun fail(message: String): Nothing {
    val lines = message.split(NL)
    if (debugMode) {
        // In debug mode, print the error lines on stderr also,
        // and ensure that all pending log lines are flushed too.
        if (logFilePath == null) logFilePath = "jaunch.log"
        report("ERROR", *lines.toTypedArray())
    }
    // Pass the error back to the Jaunch native launcher via stdout.
    // Or if in dry-run mode, emit what we would have passed, then exit nicely.
    val doOutput = { s: Any -> if (dryRunMode) dryRun(s) else emit(s) }
    doOutput("ERROR")
    doOutput(lines.size + 1)
    doOutput(EXIT_CODE_ON_FAIL)
    lines.forEach { doOutput(it) }
    if (dryRunMode) {
        emit("ABORT")
        exit(0)
    }
    exit(EXIT_CODE_ON_FAIL)
    // Unreachable code, but satisfies the Kotlin compiler.
    throw IllegalStateException(message)
}

fun emit(vararg lines: Any) {
    lines.forEach { println(it) }
}

private fun report(prefix: String, vararg args: Any) {
    val s = buildString {
        append("[$prefix] ")
        args.forEach { append(it) }
    }
    printlnErr(s)

    // Also log the line to the appropriate log file.
    val path = logFilePath
    var file = logFile
    if (path == null) {
        // Log file path is not yet known; save line to buffer.
        logLines += s
    }
    else {
        try {
            if (file == null) {
                file = File(path)
                logFile = file

                // Overwrite any log file from previous run.
                if (file.exists) file.rm()

                // Write out previously buffered lines.
                logLines.forEach { file.write("$it$NL") }
                logLines.clear()
            }
            // Write out the new line.
            file.write("$s$NL")
        }
        catch (exc: RuntimeException) {
            // Something went wrong; disable file logging.
            printlnErr("Failed to write to log file: $logFile")
            if (debugMode) printlnErr(exc.stackTraceToString())
            logFilePath = null
            logFile = null
        }
    }
}
