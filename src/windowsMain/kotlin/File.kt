@file:OptIn(ExperimentalForeignApi::class)

import kotlinx.cinterop.*
import platform.windows.*

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class File actual constructor(private val thePath: String) {

    actual val path: String
        get() {
            // TODO: How can we make this DRY with the identical version in posixMain?
            return if (thePath.startsWith("~"))
                "$USER_HOME${thePath.substring(1)}" else thePath
        }

    actual val absolutePath: String
        get() {
            return path // FIXME
        }

    actual val exists: Boolean
        get() {
            val fileAttributes = GetFileAttributesA(path)
            return fileAttributes != INVALID_FILE_ATTRIBUTES &&
                    (fileAttributes and FILE_ATTRIBUTE_DIRECTORY) == 0.convert<UInt>()
        }

    actual val isFile: Boolean
        get() {
            val fileAttributes = GetFileAttributesA(filePath)
            return fileAttributes != INVALID_FILE_ATTRIBUTES &&
                    (fileAttributes and FILE_ATTRIBUTE_DIRECTORY) == 0.convert<UInt>() &&
                    (fileAttributes and FILE_ATTRIBUTE_NORMAL) == FILE_ATTRIBUTE_NORMAL.convert<UInt>()
        }

    actual val isDirectory: Boolean
        get() {
            val fileAttributes = GetFileAttributesA(filePath)
            return fileAttributes != INVALID_FILE_ATTRIBUTES &&
                    (fileAttributes and FILE_ATTRIBUTE_DIRECTORY) == 0.convert<UInt>() &&
                    (fileAttributes and FILE_ATTRIBUTE_NORMAL) == FILE_ATTRIBUTE_NORMAL.convert<UInt>()
        }

    private fun isMode(modeBits: Int): Boolean {
        val statResult = memScoped {
            val statResult = alloc<stat>()
            stat(path, statResult.ptr)
            statResult
        }
        return (statResult.st_mode.toUInt() and modeBits.toUInt()) != 0u
    }

    actual fun listFiles(): List<File> {
        if (!isDirectory) throw IllegalArgumentException("Not a directory: $path")

        val files = mutableListOf<String>()

        val findFileData = platform.windows.WIN32_FIND_DATAW.alloc()

        val hFindFile = FindFirstFileW("$directoryPath\\*", findFileData.ptr)
        if (hFindFile == INVALID_HANDLE_VALUE) {
            return emptyList()
        }

        try {
            do {
                val fileName = findFileData.cFileName.toKString()
                if (fileName != "." && fileName != "..") {
                    files.add(fileName)
                }
            } while (FindNextFileW(hFindFile, findFileData.ptr) != 0)
        } finally {
            FindClose(hFindFile)
        }

        return files
    }

    actual fun readLines(): List<String> {
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
                var bytesRead = 0.convert<UInt>()

                if (ReadFile(fileHandle, buffer, fileSize.convert(), bytesRead.ptr, null)) {
                    lines.addAll(buffer.toKString().split('\n'))
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
