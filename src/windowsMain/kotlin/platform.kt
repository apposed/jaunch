@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.windows.*

actual fun execute(command: String): List<String>? {
    val stdout = mutableListOf<String>()

    // TODO: Might need to prepend "cmd /c" to the `command`?

    memScoped {
        val readPipe = alloc<HANDLEVar>()
        val writePipe = alloc<HANDLEVar>()

        // Create pipes for stdout redirection
        if (CreatePipe(readPipe.ptr, writePipe.ptr, null, 0) == 0) {
            println("Error creating pipe: ${GetLastError()}")
            return emptyList()
        }

        val processInfo = alloc<PROCESS_INFORMATION>()
        val startupInfo = alloc<STARTUPINFOA>().apply {
            cb = sizeOf<STARTUPINFOA>().convert()
            dwFlags = STARTF_USESTDHANDLES.convert()
            hStdOutput = writePipe.value
        }

        if (CreateProcessA(
                null,
                command,
                null,
                null,
                true,
                0,
                null,
                null,
                startupInfo.ptr,
                processInfo.ptr
            ) == 0
        ) {
            println("Error creating process: ${GetLastError()}")
            CloseHandle(readPipe.value)
            CloseHandle(writePipe.value)
            return emptyList()
        }

        // Close the write end of the pipe, as we're only reading from it
        CloseHandle(writePipe.value)

        val buffer = allocArray<ByteVar>(4096)
        var bytesRead = 0.convert<UInt>()

        do {
            if (ReadFile(readPipe.value, buffer, buffer.size.convert(), bytesRead.ptr, null) == 0) {
                break
            }

            if (bytesRead > 0) {
                stdout.add(buffer.toKString())
            }
        } while (bytesRead > 0)

        CloseHandle(readPipe.value)
        WaitForSingleObject(processInfo.hProcess, INFINITE)

        CloseHandle(processInfo.hThread)
        CloseHandle(processInfo.hProcess)
    }

    return stdout
}

actual fun getenv(name: String): String? {
    val bufferSize = 4096
    memScoped {
        val buffer = allocArray<ByteVar>(bufferSize)
        val result = GetEnvironmentVariableA(name, buffer, bufferSize.toUInt())

        if (result == 0u) {
            // The function returns 0 if the variable does not exist
            return null
        }

        return buffer.toKString()
    }
}

actual fun printlnErr(s: String) {
    memScoped {
        val stderrHandle = GetStdHandle(STD_ERROR_HANDLE)
        if (stderrHandle == INVALID_HANDLE_VALUE) {
            println("Error getting stderr handle: ${GetLastError()}")
            return
        }

        val messageBuffer = s.cstr
        var bytesWritten = 0.convert<UInt>()
        if (WriteConsoleA(stderrHandle, messageBuffer, messageBuffer.size.convert(), bytesWritten.ptr, null) == 0) {
            println("Error writing to stderr: ${GetLastError()}")
        }
    }
}

actual fun stdinLines(): Array<String> {
    val lines = mutableListOf<String>()

    memScoped {
        val stdinHandle = GetStdHandle(STD_INPUT_HANDLE)
        if (stdinHandle == INVALID_HANDLE_VALUE) {
            println("Error getting stdin handle: ${GetLastError()}")
            return emptyArray()
        }

        val buffer = allocArray<ByteVar>(4096)
        var bytesRead = 0.convert<UInt>()

        while (ReadConsoleA(stdinHandle, buffer, buffer.size.convert(), bytesRead.ptr, null) != 0) {
            if (bytesRead > 0) {
                lines.add(buffer.toKString())
            }
        }

        if (GetLastError() != ERROR_BROKEN_PIPE) {
            println("Error reading from stdin: ${GetLastError()}")
        }
    }

    return lines.toTypedArray()
}

actual fun memInfo(): MemoryInfo {
    val memInfo = MemoryInfo()
    memScoped {
        val memoryStatus = alloc<MEMORYSTATUSEX>().apply {
            dwLength = sizeOf<MEMORYSTATUSEX>().convert()
        }

        if (GlobalMemoryStatusEx(memoryStatus.ptr) != 0) {
            memInfo.total = memoryStatus.ullAvailVirtual.toLong()
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
