import kotlinx.cinterop.*
import platform.posix._popen
import platform.posix.fgets
import platform.windows.*

@OptIn(ExperimentalForeignApi::class)
actual fun execute(command: String): List<String>? {
    // Source: https://stackoverflow.com/a/69385366/1207769
    val lines = mutableListOf<String>()
    val fp = _popen(command, "r") ?: error("Failed to run command: $command")
    val buffer = ByteArray(128 * 128)
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
    val lines = mutableListOf<String>()

    memScoped {
        val stdinHandle = GetStdHandle(STD_INPUT_HANDLE)
        if (stdinHandle == INVALID_HANDLE_VALUE) {
            println("Error getting stdin handle: ${GetLastError()}")
            return emptyArray()
        }

        val size = 1024 * 1024
        val buffer = allocArray<ByteVar>(size)
        val bytesRead = alloc<DWORDVar>()
        val stdin = buildString {
            while (ReadConsoleA(stdinHandle, buffer, size.toUInt(), bytesRead.ptr, null) != 0) {
                if (bytesRead.value > 0u) {
                    append(buffer.toKString().substring(0, bytesRead.value.toInt()))
                }
            }
        }

        val error = GetLastError()
        if (error != ERROR_BROKEN_PIPE.toUInt()) {
            println("Error reading from stdin: $error")
        }
        lines.addAll(stdin.split(Regex("(\\r\\n|\\n)")))
    }

    return lines.toTypedArray()
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
