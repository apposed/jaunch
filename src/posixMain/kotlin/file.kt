import kotlinx.cinterop.*
import kotlinx.cinterop.nativeHeap.alloc
import platform.posix.*

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class File actual constructor(private val rawPath: String) {

    actual val path: String = canonicalize(rawPath)
    actual val exists: Boolean get() = access(path, F_OK) == 0
    //actual val canRead: Boolean get() = access(path, R_OK) == 0
    //actual val canWrite: Boolean get() = access(path, W_OK) == 0
    //actual val canExecute: Boolean get() = access(path, X_OK) == 0
    actual val isFile: Boolean get() = isMode(S_IFREG)
    actual val isDirectory: Boolean get() = isMode(S_IFDIR)
    actual val isRoot: Boolean = path == SLASH

    @OptIn(ExperimentalForeignApi::class)
    actual val length: Long get() = memScoped {
        val statResult = alloc<stat>()
        stat(path, statResult.ptr)
        return statResult.st_size
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun isMode(modeBits: Int): Boolean {
        val statMode = memScoped {
            val statResult = alloc<stat>()
            stat(path, statResult.ptr)
            statResult.st_mode.toInt()
        }
        return (statMode and modeBits) != 0
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun ls(): List<File> {
        if (!isDirectory) throw IllegalArgumentException("Not a directory: $path")

        val directory = opendir(path) ?: throw IllegalArgumentException("Failed to open directory")
        val files = mutableListOf<File>()

        try {
            while (true) {
                val entry = readdir(directory) ?: break
                val name = entry.pointed.d_name.toKString()
                if (name != "." && name != "..") files.add(File("$path/$name"))
            }
        }
        finally {
            closedir(directory)
        }

        return files
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun lines(): List<String> {
        val lines = mutableListOf<String>()
        memScoped {
            val file = fopen(path, "r") ?: throw RuntimeException("Failed to open file")
            try {
                while (true) {
                    val buffer = ByteArray(BUFFER_SIZE)
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

    @OptIn(ExperimentalForeignApi::class)
    actual fun mkdir(): Boolean {
        if (exists && ! isDirectory) {
            println("Error: '$path' already exists but is not a directory.")
            return false
        }
        memScoped {
            // Default permissions for new directories (read/write for owner)
            val mode = S_IRWXU.toUInt() // User can read, write, execute

            // Create the directory, returning false if it fails
            if (mkdir(path, mode) != 0) {
                val errorCode = errno
                println("Error creating directory '$path': ${strerror(errorCode)?.toKString()}")
                return false
            }

            return true // Successful directory creation
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun mv(dest: File): Boolean {
        memScoped {
            return rename(path, dest.path) == 0
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun rm(): Boolean {
        memScoped {
            return remove(path) == 0
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun rmdir(): Boolean {
        memScoped {
            return rmdir(path) == 0
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun canonicalize(path: String): String {
    var p = path

    // Expand leading tilde to user's home directory.
    if (p == "~") p = userHome()
    else if (p.startsWith("~$SLASH")) p = userHome() + p.substring(1)
    else if (p.startsWith("~")) throw IllegalArgumentException("Tilde expansion for named users is unsupported")

    // Prepend CWD to relative path.
    if (!p.startsWith(SLASH)) {
        val cwd = getcwd(null, 0u)?.toKString() ?:
            throw RuntimeException("Failed to get current working directory")
        p = "$cwd$SLASH$p"
    }

    // Split now-absolute path into components.
    val components = p.split(SLASH)

    // Canonicalize the components.
    val canonical = mutableListOf<String>()
    for (component in components) {
        when (component) {
            "", "." -> {} // Skip empty components and current directory references.
            ".." -> if (canonical.isNotEmpty()) canonical.removeLast() // Go up a directory.
            else -> canonical.add(component) // Regular component.
        }
    }
    return SLASH + canonical.joinToString(SLASH)
}
