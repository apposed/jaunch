import kotlinx.cinterop.*
import platform.posix.*
import platform.posix.getenv as pGetEnv

@OptIn(ExperimentalForeignApi::class)
actual fun execute(command: String): List<String>? {
    val stdout = mutableListOf<String>()

    val process = popen(command, "r") ?: return null
    memScoped {
        val buffer = allocArray<ByteVar>(BUFFER_SIZE)
        while (true) {
            val line = fgets(buffer, BUFFER_SIZE, process) ?: break
            stdout += line.toKString().replace(Regex("(\\r\\n|\\n)$"), "")
        }
    }
    pclose(process)

    return stdout
}

@OptIn(ExperimentalForeignApi::class)
actual fun getcwd(): String {
    return getcwd(null, 0u)?.toKString() ?: ""
}

actual fun setcwd(cwd: String) {
    chdir(cwd)
}

@OptIn(ExperimentalForeignApi::class)
actual fun getenv(name: String): String? {
    return pGetEnv(name)?.toKString()
}

@OptIn(ExperimentalForeignApi::class)
private val STDERR = fdopen(2, "w")
@OptIn(ExperimentalForeignApi::class)
actual fun printlnErr(s: String) {
    fprintf(STDERR, "%s\n", s)
    fflush(STDERR)
}

@OptIn(ExperimentalForeignApi::class)
actual fun stdinLines(): Array<String> {
    var lines = emptyArray<String>()
    memScoped {
        val buffer = allocArray<ByteVar>(BUFFER_SIZE)
        // Passing the line count as the first line lets us stop reading from stdin once we have
        // seen those lines, even though the pipe is still technically open. This avoids deadlocks.
        val numLines = fgets(buffer, BUFFER_SIZE, stdin)?.toKString()?.trim()?.toInt() ?:
            error("Expected input line count as the first line of input")
        for (i in 0..<numLines) {
            val input = fgets(buffer, BUFFER_SIZE, stdin)?.toKString()?.trim() ?: break
            lines += input
        }
    }
    return lines
}

@OptIn(ExperimentalForeignApi::class)
actual fun memInfo(): MemoryInfo {
    val memInfo = MemoryInfo()
    memScoped {
        val stat = alloc<stat>()
        if (stat("/proc/meminfo", stat.ptr) != 0) {
            // No /proc/meminfo... are we on macOS? Let's try sysctl.
            val sysctlOutput = execute("sysctl -n hw.memsize")
            val memsize = sysctlOutput?.getOrNull(0)?.trim()?.toLongOrNull()
            if (memsize != null) memInfo.total = memsize
            return memInfo
        }

        // TODO: Handle content longer than BUFFER_SIZE correctly.
        val buffer = ByteArray(BUFFER_SIZE)
        val file = fopen("/proc/meminfo", "r")
        val bytesRead = fread(buffer.refTo(0), 1u, buffer.size.toULong(), file)
        fclose(file)
        if (bytesRead == 0uL) return memInfo

        val content = buffer.toKString()
        val lines = content.lines()

        memInfo.total = lines.firstOrNull { it.startsWith("MemTotal:") }?.extractMemoryValue()
        memInfo.free = lines.firstOrNull { it.startsWith("MemFree:") }?.extractMemoryValue()
    }
    return memInfo
}

private fun String.extractMemoryValue(): Long? {
    val regex = Regex("(\\d+) kB")
    val match = regex.find(this)
    val value = match?.groupValues?.getOrNull(1)?.toLongOrNull()
    // Multiply result by 1024 to return value in bytes, not KB.
    return if (value == null) null else value * 1024
}

actual val USER_HOME = getenv("HOME")
actual val SLASH = "/"
actual val COLON = ":"
actual val NL = "\n"
