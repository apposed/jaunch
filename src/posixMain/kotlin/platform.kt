@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.*
import platform.posix.getenv as pGetEnv

actual fun execute(command: String): List<String>? {
    val stdout = mutableListOf<String>()

    val process = popen(command, "r") ?: return null
    memScoped {
        val bufferLength = 65536
        val buffer = allocArray<ByteVar>(bufferLength)
        while (true) {
            val line = fgets(buffer, bufferLength, process) ?: break
            stdout += line.toKString()
        }
    }
    pclose(process)

    return stdout
}

actual fun getenv(name: String): String? {
    return pGetEnv(name)?.toKString()
}

private val STDERR = fdopen(2, "w")
actual fun printlnErr(s: String) {
    fprintf(STDERR, "%s\n", s)
    fflush(STDERR)
}

actual fun stdinLines(): Array<String> {
    var lines = emptyArray<String>()
    memScoped {
        val bufferLength = 65536
        val buffer = allocArray<ByteVar>(bufferLength)
        // Passing the line count as the first line lets us stop reading from stdin once we have
        // seen those lines, even though the pipe is still technically open. This avoids deadlocks.
        val numLines = fgets(buffer, bufferLength, stdin)?.toKString()?.trim()?.toInt() ?:
            error("Expected input line count as the first line of input")
        for (i in 0..<numLines) {
            val input = fgets(buffer, bufferLength, stdin)?.toKString()?.trim() ?: break
            lines += input
        }
    }
    return lines
}

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

        // TODO: Handle lines longer than 64K correctly instead of crashing.
        val buffer = ByteArray(65536)
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
    if (value != null) value *= 1024
    return value
}

actual val USER_HOME = getenv("HOME")
actual val SLASH = "/"
actual val COLON = ":"
actual val NL = "\n"
