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
        val slash = path.lastIndexOf('/')
        return if (slash < 0) "." else path.substring(0, slash)
    }

val File.name: String
    get() {
        val slash = path.lastIndexOf('/')
        return if (slash < 0) path else path.substring(slash + 1)
    }

val File.suffix: String?
    get() {
        val slash = path.lastIndexOf('/')
        val dot = path.lastIndexOf('.', slash + 1)
        return if (dot < slash || dot < 0) null else path.substring(dot + 1)
    }

val File.withoutSuffix: String
    get() {
        val slash = path.lastIndexOf('/')
        val dot = path.lastIndexOf('.', slash + 1)
        return if (dot < slash || dot < 0) path else path.substring(0, dot)
    }
