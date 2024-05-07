import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
actual fun mkdir(path: String): Boolean {
    val result = mkdir(path, S_IRWXU.toUInt())
    if (result != 0) {
        val errorCode = errno
        platform.posix.warn("Error creating directory '$path': ${strerror(errorCode)?.toKString()}")
    }
    return result == 0
}
