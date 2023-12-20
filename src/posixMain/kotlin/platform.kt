@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.fgets
import platform.posix.stdin
import platform.posix.system
import platform.posix.getenv as pGetEnv

actual fun executeCommand(command: String): Int {
    return system(command)
}

actual fun getenv(name: String): String? {
    return pGetEnv(name)?.toKString()
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

actual val slash = "/"
actual val colon = ":"
