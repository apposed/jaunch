expect fun executeCommand(command: String): Int

expect fun getenv(name: String): String?

expect fun printlnErr(s: String = "")

expect fun stdinLines(): Array<String>

data class MemoryInfo(var total: Long? = null, var free: Long? = null)

expect fun memInfo(): MemoryInfo

expect val SLASH: String
expect val COLON: String
