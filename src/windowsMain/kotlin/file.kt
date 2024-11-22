import kotlinx.cinterop.*
import platform.windows.*
import kotlin.math.min

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class File actual constructor(private val rawPath: String) {

    actual val path: String = canonicalize(rawPath)

    actual val exists: Boolean get() = GetFileAttributesA(path) != INVALID_FILE_ATTRIBUTES

    //actual val canRead: Boolean get() = ...
    //actual val canWrite: Boolean get() = ...
    //actual val canExecute: Boolean get() = ...

    actual val isFile: Boolean get() = isMode(FILE_ATTRIBUTE_NORMAL)

    actual val isDirectory: Boolean get() = isMode(FILE_ATTRIBUTE_DIRECTORY)

    actual val isRoot: Boolean =
        // Is it a drive letter plus backslash (e.g. `C:\`)?
        path.length == 3 && (path[0] in 'a'..'z' || path[0] in 'A'..'Z') && path[1] == ':' && path[2] == '\\'

    @OptIn(ExperimentalForeignApi::class)
    actual val length: Long get() {
        val fileHandle = openFile(path) ?: return -1
        try {
            return fileSize(fileHandle) ?: -1
        } finally {
            CloseHandle(fileHandle)
        }
    }

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
        var lines = listOf<String>()
        memScoped {
            val fileHandle = openFile(path) ?: return lines
            try {
                val buffer = ByteArray(1024)
                val bytesRead = alloc<DWORDVar>()
                val content = StringBuilder()
                do {
                    buffer.usePinned { pinned ->
                        if (ReadFile(
                                fileHandle,
                                pinned.addressOf(0),
                                buffer.size.toUInt(),
                                bytesRead.ptr,
                                null
                            ) != 0
                        ) {
                            val readCount = bytesRead.value.toInt()
                            if (readCount > 0) {
                                content.append(buffer.decodeToString(0, readCount));
                            }
                        } else {
                            printlnErr("Error reading file: ${GetLastError()}")
                        }
                    }
                } while (bytesRead.value > 0U)
                lines = content.split(Regex("\\r\\n|\\n"))
            } finally {
                CloseHandle(fileHandle)
            }
        }
        return lines
    }

    override fun toString(): String {
        return path
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun mv(dest: File): Boolean {
        memScoped {
            val pathW = path.wcstr.ptr
            val destW = dest.path.wcstr.ptr
            val flags = MOVEFILE_REPLACE_EXISTING.toUInt()
            return MoveFileEx!!(pathW, destW, flags) != 0
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun rm(): Boolean {
        memScoped {
            return DeleteFileW(path) != 0
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun rmdir(): Boolean {
        memScoped {
            return RemoveDirectoryW(path) != 0
        }
    }

    private fun isMode(modeBits: Int): Boolean {
        val attrs = GetFileAttributesA(path)
        return attrs != INVALID_FILE_ATTRIBUTES && (attrs.toInt() and modeBits) != 0
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun openFile(path: String): HANDLE? {
        val fileHandle = CreateFileW(
            path,
            GENERIC_READ,
            FILE_SHARE_READ.toUInt(),
            null,
            OPEN_EXISTING.toUInt(),
            FILE_ATTRIBUTE_NORMAL.toUInt(),
            null
        )

        if (fileHandle == INVALID_HANDLE_VALUE) {
            warn("Error opening file: ${GetLastError()}")
            return null
        }
        return fileHandle
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun fileSize(fileHandle: HANDLE): Long? = memScoped {
        val fileSize = alloc<LARGE_INTEGER>()
        if (GetFileSizeEx(fileHandle, fileSize.ptr) == 0) {
            warn("Error getting file size: ${GetLastError()}")
            return null
        }
        return fileSize.QuadPart
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
        val fullPathLength = GetFullPathName!!(p.wcstr.ptr, bufferLength.toUInt(), buffer, null)
        if (fullPathLength == 0u) throw RuntimeException("Failed to get full path: ${GetLastError()}")
        return buffer.toKString()
    }
}
