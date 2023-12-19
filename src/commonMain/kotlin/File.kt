@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class File(path: String) {
    val exists: Boolean
    val isFile: Boolean
    val isDirectory: Boolean
    val absolutePath: String
    fun listFiles(): List<File>
}

// -- Platform-agnostic File members --

val File.directoryPath: String
    get() {
        val path = toString()  // TODO: How to access path member in an extension function?
        val slash = path.lastIndexOf('/')
        return if (slash < 0) "." else path.substring(0, slash)
    }

val File.name: String
    get() {
        val path = toString()  // TODO: How to access path member in an extension function?
        val slash = path.lastIndexOf('/')
        return if (slash < 0) path else path.substring(slash + 1)
    }

val File.withoutSuffix: String
    get() {
        val path = toString()  // TODO: How to access path member in an extension function?
        val slash = path.lastIndexOf('/')
        val dot = name.lastIndexOf('.')
        if (dot < slash) return path  // No dot in filename portion of path string.
        return if (dot < 0) path else path.substring(0, dot)
    }
