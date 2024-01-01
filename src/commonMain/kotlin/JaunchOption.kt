@Suppress("ArrayInDataClass")
data class JaunchOption(
    val flags: Array<String>,
    val assignment: String?,
    val help: String?,
) {
    fun help(): String = buildString {
        val indent = "$NL                    "
        append(flags.joinToString(", "))
        assignment?.let { append(" $it") }
        help?.let { append("$indent${it.replace("\n", indent)}") }
    }
}
