expect class File(path: String) {
    val exists: Boolean
    val isFile: Boolean
    val isDirectory: Boolean
    val absolutePath: String
    fun listFiles(): List<File>
}
