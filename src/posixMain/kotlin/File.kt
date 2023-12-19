@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.*

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class File actual constructor(val path: String) {

    actual val exists: Boolean
        get() {
            return access(path, F_OK) == 0
        }

    actual val isFile: Boolean
        get() {
            return isMode(S_IFREG)
        }

    actual val isDirectory: Boolean
        get() {
            return isMode(S_IFDIR)
        }

    private fun isMode(modeBits: Int): Boolean {
        val statResult = memScoped {
            val statResult = alloc<stat>()
            stat(path, statResult.ptr)
            statResult
        }
        return (statResult.st_mode and modeBits.toUInt()) != 0u
    }

    actual val absolutePath: String
        get() {
            return path // FIXME
        }

    actual fun listFiles(): List<File> {
        if (!isDirectory) throw IllegalArgumentException("Not a directory")

        val directory = opendir(path) ?: throw IllegalArgumentException("Cannot open directory")
        val files = mutableListOf<File>()

        try {
            while (true) {
                val entry = readdir(directory) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name != "." && name != "..") {
                    files.add(File("$path/$name"))
                }
            }
        } finally {
            closedir(directory)
        }

        return files
    }

    override fun toString(): String {
        return path
    }

    actual val directoryPath: String
        get() {
            val slash = path.lastIndexOf('/')
            return if (slash < 0) "." else path.substring(0, slash)
        }

    actual fun contents(): String {
        TODO("Not yet implemented")
    }
}
