// Platform-specific File class and functions.

const val BUFFER_SIZE = 65536

/** Abstract representation of file and directory pathnames. Always canonical! */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class File(rawPath: String) {
    val path: String
    val exists: Boolean
    //val canRead: Boolean
    //val canWrite: Boolean
    //val canExecute: Boolean
    val isFile: Boolean
    val isDirectory: Boolean
    val isRoot: Boolean
    fun ls(): List<File>
    fun lines(): List<String>
    fun mv(dest:File): Boolean
    fun rm() : Boolean
    fun rmdir() : Boolean
    fun stat() : Long
}

// -- Platform-agnostic File members --

val File.dir: File
    get() {
        require(!isRoot) { "Root directory has no parent" }
        val slash = lastSlash(path)
        return File(path.substring(0, slash))
    }

val File.name: String
    get() {
        return if (isRoot) "" else path.substring(lastSlash(path) + 1)
    }

val File.suffix: String?
    get() {
        val dot = path.lastIndexOf('.')
        return if (dot < lastSlash(path)) null else path.substring(dot + 1)
    }

/** File without the suffix. */
val File.base: File
    get() {
        val dot = path.lastIndexOf('.')
        return if (dot < lastSlash(path)) this else File(path.substring(0, dot))
    }

operator fun File.div(p: String): File = File("$path$SLASH$p")

// -- File-related utility functions --

/** Finds glob matches recursively. */
private fun glob(prefixes: List<String>, remaining: String?): List<String> {
    if (remaining.isNullOrEmpty()) return prefixes
    val slash = remaining.indexOf(SLASH)
    val name = if (slash < 0) remaining else remaining.substring(0, slash)
    val rest = if (slash < 0) null else remaining.substring(slash + 1)
    val hits = mutableListOf<String>()

    if (name.contains("*")) {
        // Glob pattern detected: calculate matching files in the same directory.
        // TODO: Escape other problematic regex-sensitive characters besides only dot.
        val re = Regex(name.replace(".", "\\.").replace("*", ".*"))
        for (prefix in prefixes) {
            val dir = File(prefix)
            if (!dir.isDirectory) continue
            for (file in dir.ls()) {
                if (re.matches(file.name)) hits += file.path
            }
        }
    }
    else {
        // It's a normal path fragment.
        for (prefix in prefixes) {
            val file = File("$prefix$SLASH$name")
            if (file.exists) hits += file.path
        }
    }

    return glob(hits, rest)
}

fun glob(path: String): List<String> {
    // Expand tilde home character.
    val expanded = path.replace("~", USER_HOME ?: fail("USER_HOME variable is unset?!"))
    // Standardize slashes.
    val p = expanded.replace("/", SLASH).replace("\\", SLASH)

    // Find the first instance of a glob.
    val star = p.indexOf("*")
    if (star < 0) return listOf(p) // No glob -- just return what we have.

    // Start with the directory prefix before the glob.
    // If there is no prefix before the first glob, it must be a relative path.
    // Or we're on Windows and they did `*:\...`, which I refuse to support. :-P
    val slash = p.lastIndexOf(SLASH, star)
    val prefix = if (slash < 0) "." else p.substring(0, slash)
    val remain = if (slash < 0) p else p.substring(slash + 1)
    return glob(listOf(prefix), remain)
}

private fun lastSlash(p: String): Int {
    val slash = p.lastIndexOf(SLASH)
    require(slash >= 0) { "Illegal path string: $p" }
    return slash
}
