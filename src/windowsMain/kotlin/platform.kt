import kotlinx.cinterop.*
import platform.posix.*
import platform.posix.getenv as pGetEnv
import platform.windows.*

actual val TARGET_ARCH = detectNativeCpuArch()

@OptIn(ExperimentalForeignApi::class)
private fun detectNativeCpuArch(): String {
    // Try environment variable first - Windows sets this reliably.
    val processorArchitecture = getenv("PROCESSOR_ARCHITECTURE")
    val processorArchiteW6432 = getenv("PROCESSOR_ARCHITEW6432")

    // PROCESSOR_ARCHITEW6432 is set when running 32/64-bit processes
    // on a different arch. It contains the actual host architecture.
    val actualArch = processorArchiteW6432 ?: processorArchitecture

    return when (actualArch?.uppercase()) {
        "AMD64", "X64" -> "X64"
        "ARM64" -> "ARM64"
        "X86" -> "X86"
        else -> {
            // Fallback: try GetNativeSystemInfo API.
            try {
                memScoped {
                    val systemInfo = alloc<SYSTEM_INFO>()
                    GetNativeSystemInfo(systemInfo.ptr)

                    when (systemInfo.wProcessorArchitecture.toInt()) {
                        PROCESSOR_ARCHITECTURE_AMD64 -> "X64"
                        PROCESSOR_ARCHITECTURE_ARM64 -> "ARM64"
                        PROCESSOR_ARCHITECTURE_INTEL -> "X86"
                        else -> /*CPU_ARCH*/"UNKNOWN-${systemInfo.wProcessorArchitecture.toInt()}"
                    }
                }
            } catch (e: Exception) {
                debug(e)
                CPU_ARCH
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun execute(command: String): List<String>? {
    // Source: https://stackoverflow.com/a/69385366/1207769
    val lines = mutableListOf<String>()
    val fp = _popen(command, "r") ?: fail("Failed to run command: $command")
    val buffer = ByteArray(BUFFER_SIZE)
    while (true) {
        val input = fgets(buffer.refTo(0), buffer.size, fp) ?: break
        // Record the line, stripping the trailing newline.
        lines.add(input.toKString().replace(Regex("(\\r\\n|\\n)$"), ""))
    }
    return lines
}

@OptIn(ExperimentalForeignApi::class)
actual fun getcwd(): String {
    memScoped {
        val buffer = allocArray<ByteVar>(MAX_PATH)
        val length = GetCurrentDirectoryA(MAX_PATH.convert(), buffer)
        return if (length > 0u) buffer.toKString() else ""
    }
}

actual fun setcwd(cwd: String) {
    SetCurrentDirectoryA(cwd)
}

@OptIn(ExperimentalForeignApi::class)
actual fun getenv(name: String): String? {
    return pGetEnv(name)?.toKString()
}

@OptIn(ExperimentalForeignApi::class)
private val STDERR = fdopen(2, "w")
@OptIn(ExperimentalForeignApi::class)
actual fun printlnErr(s: String) {
    //setvbuf(STDERR, null, _IOLBF, 0.toULong())
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
            fail("Expected input line count as the first line of input")
        for (i in 0..<numLines) {
            val input = fgets(buffer, BUFFER_SIZE, stdin)?.toKString()?.trim() ?: break
            lines += input
        }
    }
    return lines
}

@OptIn(ExperimentalForeignApi::class)
actual fun mkdir(path: String): Boolean {
    memScoped {
        val result = CreateDirectoryW(path, null)
        if (result == 0) {
            val errorCode = GetLastError()
            if (errorCode == ERROR_ALREADY_EXISTS.toUInt()) {
                return true
            } else {
                warn("Error creating directory '$path': $errorCode")
                return false
            }
        }
        return true
    }
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
            printlnErr("Error getting memory status: ${GetLastError()}")
        }
    }
    return memInfo
}

actual val USER_HOME = getenv("USERPROFILE")
actual val SLASH = "\\"
actual val COLON = ";"
actual val NL = "\r\n"
