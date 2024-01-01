import kotlinx.cinterop.*
import platform.posix.*
import platform.posix.getenv as pGetEnv
import platform.windows.*

@OptIn(ExperimentalForeignApi::class)
actual fun execute(command: String): List<String>? {
    // Source: https://stackoverflow.com/a/69385366/1207769
    val lines = mutableListOf<String>()
    val fp = _popen(command, "r") ?: error("Failed to run command: $command")
    val buffer = ByteArray(65536)
    while (true) {
        val input = fgets(buffer.refTo(0), buffer.size, fp) ?: break
        lines.add(input.toKString().replace(Regex("(\\r\\n|\\n)$"), ""))
    }
    return lines
}

@OptIn(ExperimentalForeignApi::class)
actual fun getenv(name: String): String? {
    val bufferSize = 1024 * 1024
    memScoped {
        val buffer = allocArray<ByteVar>(bufferSize)
        val result = GetEnvironmentVariableA(name, buffer, bufferSize.toUInt())
        if (result == 0u) return null // Variable does not exist.
        return buffer.toKString()
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun printlnErr(s: String) {
    memScoped {
        val stderrHandle = GetStdHandle(STD_ERROR_HANDLE)
        if (stderrHandle == INVALID_HANDLE_VALUE) {
            println("Error getting stderr handle: ${GetLastError()}")
            return
        }

        val messageBuffer = (s + NL).cstr
        val bytesWritten = alloc<DWORDVar>()
        if (WriteConsoleA(stderrHandle, messageBuffer, messageBuffer.size.toUInt(), bytesWritten.ptr, null) == 0) {
            println("Error writing to stderr: ${GetLastError()}")
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
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

@OptIn(ExperimentalForeignApi::class)
actual fun memInfo(): MemoryInfo {
    val memInfo = MemoryInfo()
    memScoped {
        val memoryStatus = alloc<MEMORYSTATUSEX>().apply {
            dwLength = sizeOf<MEMORYSTATUSEX>().toUInt()
        }

        if (GlobalMemoryStatusEx(memoryStatus.ptr) != 0) {
            memInfo.total = memoryStatus.ullTotalPhys.toLong()
            memInfo.free = memoryStatus.ullAvailPhys.toLong()
        } else {
            println("Error getting memory status: ${GetLastError()}")
        }
    }
    return memInfo
}

actual val USER_HOME = getenv("USERPROFILE")
actual val SLASH = "\\"
actual val COLON = ";"
actual val NL = "\r\n"
