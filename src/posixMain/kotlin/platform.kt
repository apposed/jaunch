@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.*
import platform.posix.getenv as pGetEnv

actual fun execute(command: String): List<String>? {
    val stdout = mutableListOf<String>()

    val process = popen(command, "r") ?: return null
    memScoped {
        val bufferLength = 1 shl 20  // 1 MB
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
        val bufferLength = 1 shl 20  // 1 MB
        val buffer = allocArray<ByteVar>(bufferLength)
        while (true) {
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
        if (stat("/proc/meminfo", stat.ptr) != 0) return memInfo

        val buffer = ByteArray(1024)
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
    return match?.groupValues?.getOrNull(1)?.toLongOrNull()
}

actual val SLASH = "/"
actual val COLON = ":"
