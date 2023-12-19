@Suppress("ArrayInDataClass")
data class JaunchOption(
    val flags: Array<String>,
    val assignment: String?,
    val help: String,
) {
    fun help(): String {
        val sb = StringBuilder()
        sb.append(flags.joinToString(", "))
        if (assignment != null) sb.append(" = $assignment")
        sb.append("\n                    $help")
        return sb.toString()
    }
}
