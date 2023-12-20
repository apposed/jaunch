@Suppress("ArrayInDataClass")
data class JaunchOption(
    val flags: Array<String>,
    val assignment: String?,
    val help: String?,
) {
    fun help(): String = buildString {
        append(flags.joinToString(", "))
        assignment?.let { append(" = $it") }
        help?.let { append("\n                    $it") }
    }
}
