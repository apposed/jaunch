import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
actual fun mkdir(path: String): Boolean {
    // NB: This function is here,
    // rather than in posixMain/platform.kt,
    // because macOS's mkdir wants a UShort,
    // whereas Linux's mkdir wants a UInt.
    // The implementations are otherwise identical.
    val result = mkdir(path, S_IRWXU.toUShort())
    if (result != 0) {
        val errorCode = errno
        platform.posix.warn("Error creating directory '$path': ${strerror(errorCode)?.toKString()}")
    }
    return result == 0
}
