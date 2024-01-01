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

fun glob(path: String): List<String> {
    val file = File(path)
    if (file.name.contains("*")) {
        // Glob pattern detected: calculate matching files in the same directory.
        val regex = Regex(file.name.replace(".", "\\.").replace("*", ".*"))
        val dir = File(file.directoryPath)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles().filter { regex.matches(it.name) }.map { it.path }
    }
    // It's a regular old file path, not a glob pattern.
    return listOf(path)
}
