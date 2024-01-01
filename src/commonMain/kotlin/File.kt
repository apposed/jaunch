@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class File(thePath: String) {
    val path: String
    val absolutePath: String
    val exists: Boolean
    val isFile: Boolean
    val isDirectory: Boolean
    fun listFiles(): List<File>
    fun readLines(): List<String>
}

// -- Platform-agnostic File members --

val File.directoryPath: String
    get() {
        val slash = path.lastIndexOf(SLASH)
        return if (slash < 0) "." else path.substring(0, slash)
    }

val File.name: String
    get() {
        val slash = path.lastIndexOf(SLASH)
        return if (slash < 0) path else path.substring(slash + 1)
    }

val File.suffix: String?
    get() {
        val slash = path.lastIndexOf(SLASH)
        val dot = path.lastIndexOf('.')
        return if (dot < slash || dot < 0) null else path.substring(dot + 1)
    }

val File.withoutSuffix: String
    get() {
        val slash = path.lastIndexOf(SLASH)
        val dot = path.lastIndexOf('.')
        return if (dot < slash || dot < 0) path else path.substring(0, dot)
    }

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
            for (file in dir.listFiles()) {
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
    val p = path.replace("~", USER_HOME ?: error("WTF no user home"))
    val star = p.indexOf("*")
    if (star < 0) return listOf(p)
    val slash = p.lastIndexOf(SLASH, star)
    val prefix = if (slash < 0) "." else p.substring(0, slash)
    val remain = if (slash < 0) p else p.substring(slash + 1)
    return glob(listOf(prefix), remain)
}
