@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.system
import platform.posix.getenv as pGetEnv

actual fun executeCommand(command: String): Int {
    return system(command)
}

actual fun getenv(name: String): String? {
    return pGetEnv(name)?.toKString()
}
