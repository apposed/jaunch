import kotlinx.cinterop.*
import platform.windows.*

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class File actual constructor(private val rawPath: String) {

    actual val path: String = canonicalize(rawPath)

    actual val exists: Boolean get() = GetFileAttributesA(path) != INVALID_FILE_ATTRIBUTES

    actual val isFile: Boolean get() = isMode(FILE_ATTRIBUTE_NORMAL)

    actual val isDirectory: Boolean get() = isMode(FILE_ATTRIBUTE_DIRECTORY)

    private fun isMode(modeBits: Int): Boolean {
        val attrs = GetFileAttributesA(path)
        return attrs != INVALID_FILE_ATTRIBUTES && (attrs.toInt() and modeBits) != 0
    }

    actual val isRoot: Boolean =
        // Is it a drive letter plus backslash (e.g. `C:\`)?
        path.length == 3 && (path[0] in 'a'..'z' || path[0] in 'A'..'Z') && path[1] == ':' && path[2] == '\\'

    @OptIn(ExperimentalForeignApi::class)
    actual fun ls(): List<File> {
        if (!isDirectory) throw IllegalArgumentException("Not a directory: $path")

        val files = mutableListOf<File>()

        memScoped {
            val findFileData = alloc<WIN32_FIND_DATAW>()

            val hFindFile = FindFirstFileW("$path\\*", findFileData.ptr)
            if (hFindFile == INVALID_HANDLE_VALUE) return emptyList()

            try {
                do {
                    val fileName = findFileData.cFileName.toKString()
                    if (fileName != "." && fileName != "..") {
                        files.add(File("$path$SLASH$fileName"))
                    }
                } while (FindNextFileW(hFindFile, findFileData.ptr) != 0)
            } finally {
                FindClose(hFindFile)
            }
        }

        return files
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun lines(): List<String> {
        val lines = mutableListOf<String>()

        memScoped {
            val fileHandle = CreateFileA(
                path,
                GENERIC_READ,
                FILE_SHARE_READ.toUInt(),
                null,
                OPEN_EXISTING.toUInt(),
                FILE_ATTRIBUTE_NORMAL.toUInt(),
                null
            )

            if (fileHandle == INVALID_HANDLE_VALUE) {
                println("Error opening file: ${GetLastError()}")
                return emptyList()
            }

            try {
                val fileSize = GetFileSize(fileHandle, null).toInt()
                val buffer = allocArray<ByteVar>(fileSize)
                val bytesRead = alloc<DWORDVar>()
                // TODO: Is bytesRead < fileSize possible? If so, do we need to loop here?
                if (ReadFile(fileHandle, buffer, fileSize.toUInt(), bytesRead.ptr, null) != 0) {
                    lines.addAll(buffer.toKString().split(Regex("(\\r\\n|\\n)")))
                } else {
                    println("Error reading file: ${GetLastError()}")
                }
            } finally {
                CloseHandle(fileHandle)
            }
        }

        return lines
    }

    override fun toString(): String {
        return path
    }
}
@OptIn(ExperimentalForeignApi::class)
private fun canonicalize(path: String): String {
    if (path.isEmpty()) return canonicalize(".")

    // Expand leading tilde to user's home directory.
    val p = when {
        path == "~" -> userHome()
        path.startsWith("~$SLASH") -> userHome() + path.substring(1)
        path.startsWith("~") -> throw IllegalArgumentException("Tilde expansion for named users is unsupported")
        else -> path
    }

    val bufferLength = MAX_PATH
    memScoped {
        val buffer = allocArray<UShortVar>(bufferLength)
        val fullPathLength = GetFullPathName?.let { it(p.wcstr.ptr, bufferLength.toUInt(), buffer, null) } ?:
            throw RuntimeException("GetFullPathName function not available")
        if (fullPathLength == 0u) throw RuntimeException("Failed to get full path: ${GetLastError()}")
        return buffer.toKString()
    }
}
