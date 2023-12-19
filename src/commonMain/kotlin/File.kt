@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class File(path: String) {
    val exists: Boolean
    val isFile: Boolean
    val isDirectory: Boolean
    val absolutePath: String
    val directoryPath: String
    fun listFiles(): List<File>
    fun contents(): String
}
