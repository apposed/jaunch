@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.posix.*

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class File actual constructor(private val thePath: String) {

    actual val path: String
        get() {
            return if (thePath.startsWith("~"))
                "$USER_HOME${thePath.substring(1)}" else thePath
        }

    actual val absolutePath: String
        get() {
            return path // FIXME
        }

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

    actual fun listFiles(): List<File> {
        if (!isDirectory) throw IllegalArgumentException("Not a directory: $path")

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

    actual fun readLines(): List<String> {
        val lines = mutableListOf<String>()
        memScoped {
            val file = fopen(path, "r") ?: throw RuntimeException("Failed to open file")
            try {
                while (true) {
                    // TODO: Handle lines longer than 1M correctly instead of crashing.
                    val buffer = ByteArray(1024 * 1024)
                    val line = fgets(buffer.refTo(0), FILENAME_MAX, file)?.toKString() ?: break
                    lines.add(line)
                }
            }
            finally {
                fclose(file)
            }
        }
        return lines
    }

    override fun toString(): String {
        return path
    }
}
